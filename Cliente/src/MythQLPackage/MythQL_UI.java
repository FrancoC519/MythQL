package MythQLPackage;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.net.URISyntaxException;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.Timer;
import javax.swing.tree.*;
import java.util.function.Consumer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

public class MythQL_UI extends JFrame {
    private JTabbedPane tabs;
    private JTextPane consolePane;
    private JPanel topPanel, leftPanel, bottomPanel;
    private String token;
    private String host;
    private int port;
    private String lastErrorMsg = "";
    
    private Map<String, List<String>> categoriasKeywords = new LinkedHashMap<>();
    private Timer highlightTimer;
    private Theme currentTheme;
    private final File themeFile = new File("current_theme.txt");
    // Conexiones separadas
    private ClienteNotificaciones clienteNotificaciones;
    private ClienteConexion clienteConsultas;
    
    private JTree schemaTree;
    private DefaultTreeModel treeModel;
    
    // Sistema de pestañas dinámico
    private int contadorPestanas = 1;
    private List<QueryTab> queryTabs = new ArrayList<>();

    // Clase interna para manejar información de cada pestaña
    private class QueryTab {
        JTextPane textPane;
        String nombreArchivo;
        boolean modificado;
        JPanel tabPanel;
        
        QueryTab(JTextPane pane, String nombre) {
            this.textPane = pane;
            this.nombreArchivo = nombre;
            this.modificado = false;
        }
    }

    public MythQL_UI(String token, String host, int port) {
        this.token = token;
        this.host = host;
        this.port = port;
        this.clienteConsultas = new ClienteConexion(host, port);
        initializeUI();
        
        cargarCategoriasKeywords("/keywordCategories.txt");
        // Configurar notificaciones DESPUÉS de que la UI esté lista
        SwingUtilities.invokeLater(() -> {
            configurarNotificaciones();
            cargarEsquemasJerarquicos();
        });
        
        // Cargar tema default
        try {
            String savedTheme = "Oscuro";
            if (themeFile.exists()) {
                savedTheme = new String(java.nio.file.Files.readAllBytes(themeFile.toPath())).trim();
            }
            currentTheme = Theme.loadFromResource("/themes/" + savedTheme + ".theme");
            aplicarTema(currentTheme);
            logMessage("Tema cargado: " + savedTheme, Color.CYAN);
        } catch (Exception e) {
            logError("Error cargando tema inicial, usando Oscuro por defecto.");
            try {
                currentTheme = Theme.loadFromResource("/themes/Oscuro.theme");
                aplicarTema(currentTheme);
            } catch (Exception ignored) {}
        }
    }

    private void initializeUI() {
        setTitle("MYTHQL");
        setSize(1100, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // ------------------- Panel superior -------------------
        topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topPanel.setBackground(new Color(108, 44, 120));
        JLabel title = new JLabel("MYTHQL");
        title.setFont(new Font("Arial Black", Font.BOLD, 22));
        title.setForeground(Color.RED);
        topPanel.add(title);

        JButton btnExecute = new JButton("Execute");
        JButton btnExecuteSel = new JButton("Execute Selected");
        JButton btnSacred = new JButton("Sacred Scroll");
        JButton btnTheme = new JButton("Change Theme");
        JButton btnRefreshSchemas = new JButton("Refresh Schemas");
        JButton btnNewTab = new JButton("+ New Query");

        topPanel.add(btnExecute);
        topPanel.add(btnExecuteSel);
        topPanel.add(btnNewTab);
        topPanel.add(btnSacred);
        topPanel.add(btnTheme);
        topPanel.add(btnRefreshSchemas);

        // Engranajes
        JButton gearGuardar = new JButton("⚙ Guardar");
        gearGuardar.setBackground(new Color(255, 204, 102));
        gearGuardar.addActionListener(e -> guardarQueryActual());
        topPanel.add(gearGuardar);
        
        JButton gearAbrir = new JButton("⚙ Abrir");
        gearAbrir.setBackground(new Color(255, 204, 102));
        gearAbrir.addActionListener(e -> AbrirQuery());
        topPanel.add(gearAbrir);
        
        JButton gearConfiguracion = new JButton("⚙ Configuracion");
        gearConfiguracion.setBackground(new Color(255, 204, 102));
        gearConfiguracion.addActionListener(e -> ConfiguracionMyth());
        topPanel.add(gearConfiguracion);

        add(topPanel, BorderLayout.NORTH);

        // ------------------- Panel izquierdo (ESQUEMAS JERÁRQUICOS) -------------------
        leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(250, 0));
        leftPanel.setBackground(new Color(242, 242, 242));

        JLabel lblManagement = new JLabel("DATABASE SCHEMAS");
        lblManagement.setFont(new Font("Arial", Font.BOLD, 12));
        lblManagement.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        leftPanel.add(lblManagement, BorderLayout.NORTH);

        // Árbol de esquemas
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Bases de Datos");
        treeModel = new DefaultTreeModel(root);
        schemaTree = new JTree(treeModel);
        schemaTree.setRootVisible(false);
        schemaTree.setShowsRootHandles(true);
        
        // Renderer personalizado para el árbol
        schemaTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                    boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                if (node.getParent() == treeModel.getRoot()) {
                    // Nodo de base de datos
                    setIcon(UIManager.getIcon("FileView.directoryIcon"));
                    setFont(getFont().deriveFont(Font.BOLD));
                } else {
                    // Nodo de tabla
                    setIcon(UIManager.getIcon("FileView.fileIcon"));
                }
                return this;
            }
        });

        // Listener para doble clic en tablas
        schemaTree.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = schemaTree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        if (node.getParent() != treeModel.getRoot() && node.getParent() != null) {
                            // Es una tabla - insertar USE y SELECT
                            String baseDatos = node.getParent().toString();
                            String tabla = node.toString();
                            String consulta = "UTILIZE " + baseDatos + ";\nBRING " + tabla;
                            getCurrentTextPane().setText(consulta);
                        } else if (node.getParent() == treeModel.getRoot()) {
                            // Es una base de datos - insertar USE
                            String baseDatos = node.toString();
                            String consulta = "UTILIZE " + baseDatos;
                            getCurrentTextPane().setText(consulta);
                        }
                    }
                }
            }
        });

        JScrollPane treeScroll = new JScrollPane(schemaTree);
        leftPanel.add(treeScroll, BorderLayout.CENTER);

        add(leftPanel, BorderLayout.WEST);

        // ------------------- Tabs con sistema dinámico -------------------
        tabs = new JTabbedPane();
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        
        // Crear la primera pestaña
        crearNuevaPestana("Query 1");
        
        add(tabs, BorderLayout.CENTER);

        // ------------------- Consola y GIF -------------------
        consolePane = new JTextPane();
        consolePane.setEditable(false);
        consolePane.setBackground(Color.BLACK);
        consolePane.setForeground(Color.WHITE);

        JScrollPane consoleScroll = new JScrollPane(consolePane);
        consoleScroll.setPreferredSize(new Dimension(0, 80));

        JButton btnGif = new JButton(new ImageIcon(getClass().getResource("/potato.gif")));
        btnGif.setBorderPainted(false);
        btnGif.setContentAreaFilled(false);
        btnGif.setFocusPainted(false);
        btnGif.setOpaque(false);
        btnGif.setPreferredSize(new Dimension(80, 80));
        btnGif.addActionListener(e -> mostrarWizard(lastErrorMsg));

        bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(consoleScroll, BorderLayout.CENTER);
        bottomPanel.add(btnGif, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        // ------------------- Listeners -------------------
        btnExecute.addActionListener(e -> ejecutarConsulta(getCurrentTextPane().getText().trim()));
        btnExecuteSel.addActionListener(e -> {
            JTextPane currentPane = getCurrentTextPane();
            String sel = currentPane.getSelectedText();
            if (sel == null || sel.isEmpty()) {
                try {
                    int caret = currentPane.getCaretPosition();
                    Element docRoot = currentPane.getDocument().getDefaultRootElement();
                    int line = docRoot.getElementIndex(caret);
                    int start = docRoot.getElement(line).getStartOffset();
                    int end = docRoot.getElement(line).getEndOffset();
                    sel = currentPane.getDocument().getText(start, end - start).trim();
                } catch (Exception ex) {
                    sel = "";
                }
            }
            ejecutarConsulta(sel);
        });

        btnNewTab.addActionListener(e -> {
            contadorPestanas++;
            crearNuevaPestana("Query " + contadorPestanas);
        });

        btnSacred.addActionListener(e -> abrirSacredScroll());
        btnTheme.addActionListener(e -> openThemeSelector());
        btnRefreshSchemas.addActionListener(e -> cargarEsquemasJerarquicos());
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    clienteConsultas.enviarConsultaConToken(token, "LOGOUT " + token);
                } catch (Exception ex) {
                    System.out.println("Error enviando logout: " + ex.getMessage());
                }
                if (clienteNotificaciones != null) {
                    clienteNotificaciones.detener();
                }
            }
        });
    }

    private void crearNuevaPestana(String nombreInicial) {
        JTextPane queryPane = new JTextPane();
        queryPane.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane scroll = new JScrollPane(queryPane);
        
        // Crear QueryTab
        QueryTab queryTab = new QueryTab(queryPane, nombreInicial);
        queryTabs.add(queryTab);
        
        // Crear panel personalizado para la pestaña con botón de cerrar
        JPanel tabPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        tabPanel.setOpaque(false);
        
        JLabel tabLabel = new JLabel(nombreInicial);
        tabLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        
        // Hacer que el label sea editable con doble clic
        tabLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    renombrarPestana(queryTab, tabLabel);
                }
            }
        });
        
        JButton btnClose = new JButton("×");
        btnClose.setPreferredSize(new Dimension(20, 20));
        btnClose.setMargin(new Insets(0, 0, 0, 0));
        btnClose.setBorderPainted(false);
        btnClose.setContentAreaFilled(false);
        btnClose.setFocusPainted(false);
        btnClose.setFont(new Font("Arial", Font.BOLD, 16));
        btnClose.addActionListener(e -> cerrarPestana(queryTab));
        
        tabPanel.add(tabLabel);
        tabPanel.add(btnClose);
        
        queryTab.tabPanel = tabPanel;
        
        // Agregar la pestaña
        tabs.addTab(null, scroll);
        int index = tabs.getTabCount() - 1;
        tabs.setTabComponentAt(index, tabPanel);
        tabs.setSelectedIndex(index);
        
        // Listener para detectar cambios en el documento
        queryPane.getDocument().addDocumentListener(new SimpleDocumentListener() {
            @Override
            public void update(DocumentEvent e) {
                marcarComoModificado(queryTab);
                
                if (highlightTimer != null && highlightTimer.isRunning()) {
                    highlightTimer.stop();
                }
                highlightTimer = new Timer(300, evt -> {
                    resaltarKeywordsCompleto(queryPane);
                });
                highlightTimer.setRepeats(false);
                highlightTimer.start();
            }
        });
    }

    private void cerrarPestana(QueryTab queryTab) {
        // Si está modificado, preguntar antes de cerrar
        if (queryTab.modificado) {
            int respuesta = JOptionPane.showConfirmDialog(
                this,
                "El query '" + queryTab.nombreArchivo + "' tiene cambios sin guardar. ¿Desea guardarlo antes de cerrar?",
                "Confirmar cierre",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            if (respuesta == JOptionPane.YES_OPTION) {
                guardarQueryEspecifico(queryTab);
            } else if (respuesta == JOptionPane.CANCEL_OPTION) {
                return; // No cerrar
            }
        }
        
        // No permitir cerrar si es la única pestaña
        if (queryTabs.size() <= 1) {
            logMessage("Debe haber al menos una pestaña abierta", Color.YELLOW);
            return;
        }
        
        // Encontrar el índice y remover
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof JScrollPane) {
                JScrollPane scroll = (JScrollPane) tabs.getComponentAt(i);
                if (scroll.getViewport().getView() == queryTab.textPane) {
                    tabs.removeTabAt(i);
                    queryTabs.remove(queryTab);
                    logMessage("Pestaña '" + queryTab.nombreArchivo + "' cerrada", Color.CYAN);
                    break;
                }
            }
        }
    }

    private void renombrarPestana(QueryTab queryTab, JLabel tabLabel) {
        String nuevoNombre = JOptionPane.showInputDialog(
            this,
            "Ingrese el nuevo nombre para la pestaña:",
            queryTab.nombreArchivo
        );
        
        if (nuevoNombre != null && !nuevoNombre.trim().isEmpty()) {
            queryTab.nombreArchivo = nuevoNombre.trim();
            actualizarEtiquetaPestana(queryTab, tabLabel);
            logMessage("Pestaña renombrada a: " + nuevoNombre, Color.CYAN);
        }
    }

    private void marcarComoModificado(QueryTab queryTab) {
        if (!queryTab.modificado) {
            queryTab.modificado = true;
            // Actualizar la etiqueta para mostrar el asterisco
            for (int i = 0; i < tabs.getTabCount(); i++) {
                if (tabs.getComponentAt(i) instanceof JScrollPane) {
                    JScrollPane scroll = (JScrollPane) tabs.getComponentAt(i);
                    if (scroll.getViewport().getView() == queryTab.textPane) {
                        Component tabComponent = tabs.getTabComponentAt(i);
                        if (tabComponent instanceof JPanel) {
                            JPanel panel = (JPanel) tabComponent;
                            if (panel.getComponent(0) instanceof JLabel) {
                                actualizarEtiquetaPestana(queryTab, (JLabel) panel.getComponent(0));
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    private void actualizarEtiquetaPestana(QueryTab queryTab, JLabel label) {
        String nombre = queryTab.nombreArchivo;
        if (queryTab.modificado) {
            nombre = "* " + nombre;
        }
        label.setText(nombre);
    }

    private QueryTab getCurrentQueryTab() {
        int index = tabs.getSelectedIndex();
        if (index >= 0 && index < tabs.getTabCount()) {
            Component comp = tabs.getComponentAt(index);
            if (comp instanceof JScrollPane) {
                JScrollPane scroll = (JScrollPane) comp;
                JTextPane pane = (JTextPane) scroll.getViewport().getView();
                for (QueryTab qt : queryTabs) {
                    if (qt.textPane == pane) {
                        return qt;
                    }
                }
            }
        }
        return null;
    }

    private JTextPane getCurrentTextPane() {
        QueryTab currentTab = getCurrentQueryTab();
        return currentTab != null ? currentTab.textPane : null;
    }

    private void configurarNotificaciones() {
        try {
            clienteNotificaciones = new ClienteNotificaciones(host, port);
            boolean exito = clienteNotificaciones.conectarYSuscribir(token, this::mostrarNotificacion);
            
            if (exito) {
                logMessage("Notificaciones en tiempo real ACTIVADAS", Color.CYAN);
                System.out.println("Cliente de notificaciones conectado y escuchando...");
            } else {
                logError("No se pudieron activar las notificaciones");
            }
            
        } catch (Exception e) {
            logError("Error configurando notificaciones: " + e.getMessage());
        }
    }

    private void mostrarNotificacion(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            logMessage("[NOTIFICACION] " + mensaje, Color.ORANGE);
            
            // Actualizar esquemas cuando hay cambios estructurales
            if (mensaje.contains("creada") || mensaje.contains("eliminada") || 
                mensaje.contains("insertados") || mensaje.contains("cambió base")) {
                cargarEsquemasJerarquicos();
            }
        });
    }

    private void cargarEsquemasJerarquicos() {
        try {
            String respuesta = clienteConsultas.obtenerEsquemas(token);
            
            if (respuesta.startsWith("ERROR")) {
                logError("Error cargando esquemas: " + respuesta);
                return;
            }
            
            actualizarArbolEsquemas(respuesta);
            logMessage("Esquemas actualizados", Color.CYAN);
            
        } catch (Exception e) {
            logError("Error cargando esquemas: " + e.getMessage());
        }
    }

    private void actualizarArbolEsquemas(String datosEsquemas) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Bases de Datos");
        
        try {
            // Formato: base1{tabla1,tabla2};base2{tabla3,tabla4}
            String[] bases = datosEsquemas.split(";");
            for (String base : bases) {
                int inicioTablas = base.indexOf('{');
                int finTablas = base.indexOf('}');
                
                if (inicioTablas != -1 && finTablas != -1) {
                    String nombreBase = base.substring(0, inicioTablas);
                    String tablasStr = base.substring(inicioTablas + 1, finTablas);
                    
                    DefaultMutableTreeNode baseNode = new DefaultMutableTreeNode(nombreBase);
                    
                    if (!tablasStr.isEmpty()) {
                        String[] tablas = tablasStr.split(",");
                        for (String tabla : tablas) {
                            if (!tabla.trim().isEmpty()) {
                                baseNode.add(new DefaultMutableTreeNode(tabla.trim()));
                            }
                        }
                    }
                    
                    root.add(baseNode);
                }
            }
            
            treeModel.setRoot(root);
            treeModel.reload();
            
            // Expandir todos los nodos
            for (int i = 0; i < schemaTree.getRowCount(); i++) {
                schemaTree.expandRow(i);
            }
            
        } catch (Exception e) {
            logError("Error parseando esquemas: " + e.getMessage());
        }
    }

    private void ejecutarConsulta(String consulta) {
        if (consulta == null || consulta.isEmpty()) {
            logError("No hay consulta para ejecutar.");
            return;
        }

        List<String> comandos = dividirPorPuntoYComa(consulta);

        for (String comando : comandos) {
            comando = comando.trim();
            if (comando.isEmpty()) continue;

            GestorSintaxis GS = new GestorSintaxis(this);
            try {
                if (GS.enviarConsulta(comando)) {
                    try {
                        String respuestaServidor = clienteConsultas.enviarConsultaConToken(token, comando);

                        logMessageWithoutEnter("Respuesta del servidor: ", Color.WHITE);

                        // ========== MANEJO DE RESPUESTAS DE TRANSACCIONES ==========
                        String comandoUpper = comando.toUpperCase();

                        if (respuestaServidor.startsWith("RESULT ERROR") || respuestaServidor.startsWith("ERROR")) {
                            logMessage(respuestaServidor, Color.RED);
                        } else if (comandoUpper.startsWith("BRING")) {
                            mostrarTablaBring(respuestaServidor);
                        } else if (comandoUpper.startsWith("START")) {
                            // Respuesta para START
                            if (respuestaServidor.startsWith("OK")) {
                                logMessage("✅ " + respuestaServidor, Color.CYAN);
                            } else {
                                logMessage(respuestaServidor, Color.ORANGE);
                            }
                        } else if (comandoUpper.startsWith("SEAL")) {
                            // Respuesta para SEAL
                            if (respuestaServidor.startsWith("OK")) {
                                logMessage("🔒 " + respuestaServidor, Color.CYAN);
                            } else {
                                logMessage(respuestaServidor, Color.ORANGE);
                            }
                        } else if (comandoUpper.startsWith("UNDO")) {
                            // Respuesta para UNDO
                            if (respuestaServidor.startsWith("OK")) {
                                logMessage("↩️ " + respuestaServidor, Color.CYAN);
                            } else {
                                logMessage(respuestaServidor, Color.ORANGE);
                            }
                        } else {
                            // Para otros comandos (SUMMON, BURN, FILE, etc.)
                            logMessage(respuestaServidor, Color.GREEN);
                        }

                        // ========== ACTUALIZAR ESQUEMAS PARA COMANDOS QUE MODIFICAN ==========
                        if (comandoUpper.startsWith("SUMMON") || 
                            comandoUpper.startsWith("BURN") ||
                            comandoUpper.startsWith("UTILIZE") ||
                            comandoUpper.startsWith("FILE") || 
                            comandoUpper.startsWith("MORPH") ||
                            comandoUpper.startsWith("SWEEP") ||
                            comandoUpper.startsWith("REWRITE") ||
                            comandoUpper.startsWith("UNDO")) {  // UNDO puede modificar el estado

                            cargarEsquemasJerarquicos();
                        }

                        Thread.sleep(150);

                    } catch (Exception ex) {
                        logError("Error al conectar con servidor: " + ex.getMessage());
                        break;
                    }
                } else {
                    logError("ERROR de sintaxis: " + comando);
                    break;
                }
            } catch (Exception e) {
                logError("ERROR inesperado: " + e.getMessage());
                break;
            }
        }
    }

    private List<String> dividirPorPuntoYComa(String texto) {
        List<String> comandos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean dentroComillasSimples = false;
        boolean dentroComillasDobles = false;

        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);

            if (c == '\'' && !dentroComillasDobles) {
                dentroComillasSimples = !dentroComillasSimples;
            } else if (c == '"' && !dentroComillasSimples) {
                dentroComillasDobles = !dentroComillasDobles;
            }

            if (c == ';' && !dentroComillasSimples && !dentroComillasDobles) {
                comandos.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }

        if (actual.length() > 0) {
            comandos.add(actual.toString());
        }

        return comandos;
    }

    private void logMessage(String message, Color color) {
        StyledDocument doc = consolePane.getStyledDocument();
        Style style = consolePane.addStyle("Style", null);
        StyleConstants.setForeground(style, color);
        try {
            doc.insertString(doc.getLength(), message + "\n", style);
            consolePane.setCaretPosition(doc.getLength());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void logError(String errorMsg) {
        lastErrorMsg = errorMsg;
        logMessage(errorMsg, Color.RED);
    }
    
    private void logMessageWithoutEnter(String message, Color color) {
        StyledDocument doc = consolePane.getStyledDocument();
        Style style = consolePane.addStyle("Style", null);
        StyleConstants.setForeground(style, color);
        try {
            doc.insertString(doc.getLength(), message, style);
            consolePane.setCaretPosition(doc.getLength());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

private void mostrarWizard(String errorMsg) {
        // En lugar de mostrar una ventana, mostrar el mensaje en la consola
        logMessage("[WIZARD] Error: " + errorMsg, Color.YELLOW);
        logMessage("[WIZARD] Consulte el Sacred Scroll para más información", Color.YELLOW);
    }

    private void guardarQueryActual() {
        QueryTab currentTab = getCurrentQueryTab();
        if (currentTab == null) {
            logError("No hay pestaña activa");
            return;
        }
        
        guardarQueryEspecifico(currentTab);
    }

    private void guardarQueryEspecifico(QueryTab queryTab) {
        String contenido = queryTab.textPane.getText().trim();

        if (contenido.isEmpty()) {
            logMessage("El query está vacío, nada que guardar.", Color.YELLOW);
            return;
        }

        // Solicitar nombre de archivo al usuario
        String nombreSugerido = queryTab.nombreArchivo.endsWith(".mql") ? 
            queryTab.nombreArchivo : queryTab.nombreArchivo + ".mql";
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Guardar Query");
        fileChooser.setSelectedFile(new File(nombreSugerido));
        
        javax.swing.filechooser.FileNameExtensionFilter filtroMQL = 
            new javax.swing.filechooser.FileNameExtensionFilter("MythQL Files (*.mql)", "mql");
        fileChooser.addChoosableFileFilter(filtroMQL);
        fileChooser.setFileFilter(filtroMQL);
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));

        int resultado = fileChooser.showSaveDialog(this);

        if (resultado == JFileChooser.APPROVE_OPTION) {
            File archivo = fileChooser.getSelectedFile();
            
            // Asegurar que tenga extensión .mql
            if (!archivo.getName().toLowerCase().endsWith(".mql")) {
                archivo = new File(archivo.getAbsolutePath() + ".mql");
            }

            try {
                Path path = archivo.toPath();
                Files.write(path, contenido.getBytes(StandardCharsets.UTF_8),
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                // Actualizar el nombre de la pestaña
                queryTab.nombreArchivo = archivo.getName().replace(".mql", "");
                queryTab.modificado = false;
                
                // Actualizar la etiqueta visual
                for (int i = 0; i < tabs.getTabCount(); i++) {
                    if (tabs.getComponentAt(i) instanceof JScrollPane) {
                        JScrollPane scroll = (JScrollPane) tabs.getComponentAt(i);
                        if (scroll.getViewport().getView() == queryTab.textPane) {
                            Component tabComponent = tabs.getTabComponentAt(i);
                            if (tabComponent instanceof JPanel) {
                                JPanel panel = (JPanel) tabComponent;
                                if (panel.getComponent(0) instanceof JLabel) {
                                    actualizarEtiquetaPestana(queryTab, (JLabel) panel.getComponent(0));
                                }
                            }
                            break;
                        }
                    }
                }

                logMessage("Query guardado en " + archivo.getName(), Color.GREEN);
            } catch (Exception ex) {
                logError("Error al guardar el archivo: " + ex.getMessage());
            }
        } else {
            logMessage("Operación de guardado cancelada", Color.YELLOW);
        }
    }
    
    private void AbrirQuery() {
        JFileChooser fileChooser = new JFileChooser();

        fileChooser.setDialogTitle("Abrir Query");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        javax.swing.filechooser.FileNameExtensionFilter filtroMQL = 
            new javax.swing.filechooser.FileNameExtensionFilter("MythQL Files (*.mql)", "mql");
        fileChooser.addChoosableFileFilter(filtroMQL);

        javax.swing.filechooser.FileNameExtensionFilter filtroTXT = 
            new javax.swing.filechooser.FileNameExtensionFilter("Text Files (*.txt)", "txt");
        fileChooser.addChoosableFileFilter(filtroTXT);

        fileChooser.setAcceptAllFileFilterUsed(true);
        fileChooser.setFileFilter(filtroMQL);
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));

        int resultado = fileChooser.showOpenDialog(this);

        if (resultado == JFileChooser.APPROVE_OPTION) {
            File archivoSeleccionado = fileChooser.getSelectedFile();

            try {
                Path path = archivoSeleccionado.toPath();
                String contenido = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

                // Crear nueva pestaña con el nombre del archivo
                String nombreArchivo = archivoSeleccionado.getName().replace(".mql", "").replace(".txt", "");
                crearNuevaPestana(nombreArchivo);
                
                // Obtener la pestaña recién creada y establecer el contenido
                QueryTab nuevaTab = queryTabs.get(queryTabs.size() - 1);
                nuevaTab.textPane.setText(contenido);
                nuevaTab.modificado = false;

                resaltarKeywordsCompleto(nuevaTab.textPane);

                logMessage("Query cargado desde: " + archivoSeleccionado.getName(), Color.GREEN);

            } catch (Exception ex) {
                logError("Error al abrir el archivo: " + ex.getMessage());
            }
        } else {
            logMessage("Operación de apertura cancelada", Color.YELLOW);
        }
    }

    private void ConfiguracionMyth() {
        JDialog dialog = new JDialog(this, "⚙️ MYTHQL - CONFIGURACIÓN DEL SISTEMA", true);
        dialog.setSize(1000, 650);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());
        dialog.setResizable(true);

        // ==================== PANEL SUPERIOR (HEADER) ====================
        JPanel panelHeader = new JPanel(new BorderLayout());
        panelHeader.setBackground(new Color(108, 44, 120));
        panelHeader.setPreferredSize(new Dimension(0, 70));
        panelHeader.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JLabel lblTitulo = new JLabel("MYTHQL CONFIGURATION PANEL");
        lblTitulo.setFont(new Font("Arial Black", Font.BOLD, 24));
        lblTitulo.setForeground(Color.RED);

        JLabel lblSubtitulo = new JLabel("Sistema de Gestión Avanzada");
        lblSubtitulo.setFont(new Font("Arial", Font.ITALIC, 12));
        lblSubtitulo.setForeground(new Color(255, 204, 204));

        JPanel panelTitulos = new JPanel(new GridLayout(2, 1));
        panelTitulos.setBackground(new Color(108, 44, 120));
        panelTitulos.add(lblTitulo);
        panelTitulos.add(lblSubtitulo);

        panelHeader.add(panelTitulos, BorderLayout.WEST);

        dialog.add(panelHeader, BorderLayout.NORTH);

        // ==================== PANEL IZQUIERDO (MENÚ) ====================
        JPanel panelMenu = new JPanel();
        panelMenu.setLayout(new BoxLayout(panelMenu, BoxLayout.Y_AXIS));
        panelMenu.setPreferredSize(new Dimension(250, 0));
        panelMenu.setBackground(new Color(30, 30, 35));
        panelMenu.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 2, new Color(108, 44, 120)));

        // Título del menú
        JPanel menuHeader = new JPanel(new FlowLayout(FlowLayout.CENTER));
        menuHeader.setBackground(new Color(40, 40, 45));
        menuHeader.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
        menuHeader.setMaximumSize(new Dimension(250, 60));

        JLabel lblMenuTitulo = new JLabel("⚙️ MÓDULOS DEL SISTEMA");
        lblMenuTitulo.setFont(new Font("Arial", Font.BOLD, 13));
        lblMenuTitulo.setForeground(new Color(255, 204, 102));
        menuHeader.add(lblMenuTitulo);

        panelMenu.add(menuHeader);
        panelMenu.add(Box.createVerticalStrut(10));

        JPanel panelContenido = new JPanel(new CardLayout());
        panelContenido.setBackground(new Color(242, 242, 242));

        JPanel panelUsuarios = crearPanelUsuarios();
        JPanel panelPrivilegios = crearPanelPrivilegios();

        panelContenido.add(panelUsuarios, "Usuarios");
        panelContenido.add(panelPrivilegios, "Privilegios");

        CardLayout cardLayout = (CardLayout) panelContenido.getLayout();

        String[] opciones = {"Usuarios", "Privilegios"};
        String[] iconos = {"👤⚙", "🔐⚙"};

        for (int i = 0; i < opciones.length; i++) {
            String opcion = opciones[i];
            String icono = iconos[i];

            JButton btnOpcion = new JButton("<html><div style='padding:5px'>" + icono + " <b>" + opcion + "</b></div></html>");
            btnOpcion.setFont(new Font("Arial", Font.PLAIN, 14));
            btnOpcion.setForeground(Color.WHITE);
            btnOpcion.setBackground(new Color(50, 50, 55));
            btnOpcion.setFocusPainted(false);
            btnOpcion.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(70, 70, 75)),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
            ));
            btnOpcion.setAlignmentX(Component.LEFT_ALIGNMENT);
            btnOpcion.setMaximumSize(new Dimension(250, 55));
            btnOpcion.setMinimumSize(new Dimension(250, 55));
            btnOpcion.setPreferredSize(new Dimension(250, 55));
            btnOpcion.setHorizontalAlignment(SwingConstants.LEFT);
            btnOpcion.setCursor(new Cursor(Cursor.HAND_CURSOR));

            btnOpcion.addActionListener(e -> {
                cardLayout.show(panelContenido, opcion);
                for (Component comp : panelMenu.getComponents()) {
                    if (comp instanceof JButton) {
                        comp.setBackground(new Color(50, 50, 55));
                    }
                }
                btnOpcion.setBackground(new Color(108, 44, 120));
            });

            btnOpcion.addMouseListener(new MouseAdapter() {
                Color currentBg = new Color(50, 50, 55);

                @Override
                public void mouseEntered(MouseEvent e) {
                    currentBg = btnOpcion.getBackground();
                    btnOpcion.setBackground(new Color(80, 35, 90));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    btnOpcion.setBackground(currentBg);
                }
            });

            panelMenu.add(btnOpcion);
        }

        panelMenu.add(Box.createVerticalGlue());

        JButton btnCerrar = new JButton("✖ Cerrar Configuración");
        btnCerrar.setFont(new Font("Arial", Font.BOLD, 12));
        btnCerrar.setForeground(Color.WHITE);
        btnCerrar.setBackground(new Color(139, 0, 0));
        btnCerrar.setFocusPainted(false);
        btnCerrar.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));
        btnCerrar.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnCerrar.setMaximumSize(new Dimension(250, 45));
        btnCerrar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnCerrar.addActionListener(e -> dialog.dispose());

        btnCerrar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btnCerrar.setBackground(new Color(180, 0, 0));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btnCerrar.setBackground(new Color(139, 0, 0));
            }
        });

        panelMenu.add(Box.createVerticalStrut(10));
        panelMenu.add(btnCerrar);
        panelMenu.add(Box.createVerticalStrut(10));

        dialog.add(panelMenu, BorderLayout.WEST);
        dialog.add(panelContenido, BorderLayout.CENTER);

        cardLayout.show(panelContenido, "Usuarios");

        dialog.setVisible(true);
    }

    // ==================== PANELES DE CONTENIDO ====================

private JPanel crearPanelUsuarios() {
    JPanel panel = new JPanel(new BorderLayout(15, 15));
    panel.setBackground(new Color(242, 242, 242));
    panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

    JPanel headerPanel = new JPanel(new BorderLayout());
    headerPanel.setBackground(Color.WHITE);
    headerPanel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 3, 0, new Color(108, 44, 120)),
        BorderFactory.createEmptyBorder(15, 20, 15, 20)
    ));

    JLabel titulo = new JLabel("👤 GESTIÓN DE USUARIOS DEL SISTEMA");
    titulo.setFont(new Font("Arial Black", Font.BOLD, 18));
    titulo.setForeground(new Color(108, 44, 120));

    JLabel subtitulo = new JLabel("Administrar cuentas de usuario y permisos de acceso");
    subtitulo.setFont(new Font("Arial", Font.ITALIC, 12));
    subtitulo.setForeground(Color.GRAY);

    JPanel titulosPanel = new JPanel(new GridLayout(2, 1, 0, 5));
    titulosPanel.setBackground(Color.WHITE);
    titulosPanel.add(titulo);
    titulosPanel.add(subtitulo);

    headerPanel.add(titulosPanel, BorderLayout.WEST);
    panel.add(headerPanel, BorderLayout.NORTH);

    // Tabla de usuarios
    String[] columnNames = {"Usuario", "Roles/Privilegios", "Estado"};
    DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false; // Hacer la tabla no editable
        }
    };
    JTable tablaUsuarios = new JTable(tableModel);
    tablaUsuarios.setRowHeight(30);
    tablaUsuarios.setFont(new Font("Arial", Font.PLAIN, 13));
    tablaUsuarios.getTableHeader().setFont(new Font("Arial", Font.BOLD, 13));
    tablaUsuarios.getTableHeader().setBackground(new Color(108, 44, 120));
    tablaUsuarios.getTableHeader().setForeground(Color.WHITE);
    tablaUsuarios.setSelectionBackground(new Color(200, 180, 210));

    JScrollPane scrollTabla = new JScrollPane(tablaUsuarios);
    scrollTabla.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180)));

    // Panel de contenido
    JPanel contenido = new JPanel(new BorderLayout(15, 15));
    contenido.setBackground(Color.WHITE);
    contenido.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
        BorderFactory.createEmptyBorder(20, 20, 20, 20)
    ));

    contenido.add(scrollTabla, BorderLayout.CENTER);

    // Panel de botones
    JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    panelBotones.setBackground(Color.WHITE);

    JButton btnAgregar = crearBotonRobusto("➕ Agregar Usuario", new Color(34, 139, 34));
    JButton btnModificar = crearBotonRobusto("✏️ Modificar Roles", new Color(30, 144, 255));
    JButton btnEliminar = crearBotonRobusto("🗑 Eliminar Usuario", new Color(220, 20, 60));
    JButton btnRefresh = crearBotonRobusto("🔄 Actualizar", new Color(108, 44, 120));

    panelBotones.add(btnAgregar);
    panelBotones.add(btnModificar);
    panelBotones.add(btnEliminar);
    panelBotones.add(btnRefresh);

    contenido.add(panelBotones, BorderLayout.SOUTH);
    panel.add(contenido, BorderLayout.CENTER);

    // ========== FUNCIONALIDAD REAL ==========
    
    // Método para cargar usuarios desde el servidor
    Runnable cargarUsuarios = () -> {
        try {
            // Enviar comando para listar usuarios (usaremos MANIFEST USERS como ejemplo)
            // Por ahora simularemos con datos de prueba
            tableModel.setRowCount(0); // Limpiar tabla
            
            // En un sistema real, aquí enviarías un comando al servidor
            // String respuesta = clienteConsultas.enviarConsultaConToken(token, "MANIFEST USERS");
            
            // Datos de ejemplo basados en el users.csv
            Object[][] datosEjemplo = {
                {"admin", "ADMIN;SELECT;INSERT;UPDATE;DELETE;CREATE;DROP;GRANT;REVOKE", "Activo"},
                {"linares", "MANAGER;SELECT;INSERT;UPDATE;DELETE;CREATE;DROP", "Activo"},
                {"TEST", "READER;SELECT", "Activo"},
                {"usuario1", "WRITER;SELECT;INSERT;UPDATE", "Activo"},
                {"usuario2", "READER;SELECT", "Activo"}
            };
            
            for (Object[] fila : datosEjemplo) {
                tableModel.addRow(fila);
            }
            
        } catch (Exception ex) {
            logError("Error cargando usuarios: " + ex.getMessage());
        }
    };

    // Cargar usuarios al iniciar
    cargarUsuarios.run();

    // Listeners de botones
    btnAgregar.addActionListener(e -> {
        JDialog dialog = new JDialog(this, "Agregar Nuevo Usuario", true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new GridLayout(0, 1, 10, 10));
        dialog.setResizable(false);

        JPanel panelForm = new JPanel(new GridLayout(4, 2, 10, 10));
        panelForm.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JTextField txtUsuario = new JTextField();
        JPasswordField txtPassword = new JPasswordField();
        JComboBox<String> cmbRol = new JComboBox<>(new String[]{"READER", "WRITER", "MANAGER", "ADMIN"});

        panelForm.add(new JLabel("Usuario:"));
        panelForm.add(txtUsuario);
        panelForm.add(new JLabel("Contraseña:"));
        panelForm.add(txtPassword);
        panelForm.add(new JLabel("Rol:"));
        panelForm.add(cmbRol);

        dialog.add(panelForm);

        JPanel panelBotonesDialog = new JPanel(new FlowLayout());
        JButton btnCrear = new JButton("Crear Usuario");
        JButton btnCancelar = new JButton("Cancelar");

        btnCrear.addActionListener(ev -> {
            String usuario = txtUsuario.getText().trim();
            String password = new String(txtPassword.getPassword());
            String rol = (String) cmbRol.getSelectedItem();

            if (usuario.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Complete todos los campos", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Ejecutar comando INVOKE
            String comando = String.format("INVOKE USER %s {\"%s\", %s}", usuario, password, rol);
            ejecutarConsultaConfiguracion(comando, "Usuario creado exitosamente");
            
            dialog.dispose();
            cargarUsuarios.run(); // Recargar lista
        });

        btnCancelar.addActionListener(ev -> dialog.dispose());

        panelBotonesDialog.add(btnCrear);
        panelBotonesDialog.add(btnCancelar);
        dialog.add(panelBotonesDialog);

        dialog.setVisible(true);
    });

    btnModificar.addActionListener(e -> {
        int selectedRow = tablaUsuarios.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Seleccione un usuario para modificar", "Advertencia", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String usuario = (String) tableModel.getValueAt(selectedRow, 0);
        String rolesActuales = (String) tableModel.getValueAt(selectedRow, 1);

        JDialog dialog = new JDialog(this, "Modificar Privilegios: " + usuario, true);
        dialog.setSize(500, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel panelPrivilegios = new JPanel(new GridLayout(0, 2, 10, 5));
        panelPrivilegios.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Lista de privilegios disponibles
        String[] privilegios = {"BRING", "FILE", "REWRITE", "BURN", "SUMMON", "SWEEP", "EMPOWER", "DISARM"};
        JCheckBox[] checkBoxes = new JCheckBox[privilegios.length];

        // Marcar privilegios actuales
        for (int i = 0; i < privilegios.length; i++) {
            checkBoxes[i] = new JCheckBox(privilegios[i]);
            checkBoxes[i].setSelected(rolesActuales.contains(privilegios[i]));
            panelPrivilegios.add(checkBoxes[i]);
        }

        dialog.add(new JScrollPane(panelPrivilegios), BorderLayout.CENTER);

        JPanel panelBotonesDialog = new JPanel(new FlowLayout());
        JButton btnAplicar = new JButton("Aplicar Cambios");
        JButton btnCancelar = new JButton("Cancelar");

        btnAplicar.addActionListener(ev -> {
            // Quitar todos los privilegios primero
            for (String priv : privilegios) {
                String comandoQuitar = String.format("DISARM %s {%s}", usuario, priv);
                ejecutarConsultaConfiguracion(comandoQuitar, null); // No mostrar mensaje para cada uno
            }

            // Agregar privilegios seleccionados
            List<String> privilegiosSeleccionados = new ArrayList<>();
            for (int i = 0; i < checkBoxes.length; i++) {
                if (checkBoxes[i].isSelected()) {
                    String comandoAgregar = String.format("EMPOWER %s {%s}", usuario, privilegios[i]);
                    ejecutarConsultaConfiguracion(comandoAgregar, null);
                    privilegiosSeleccionados.add(privilegios[i]);
                }
            }

            JOptionPane.showMessageDialog(dialog, 
                "Privilegios actualizados para: " + usuario + "\n" +
                "Privilegios: " + String.join(", ", privilegiosSeleccionados),
                "Éxito", JOptionPane.INFORMATION_MESSAGE);
            
            dialog.dispose();
            cargarUsuarios.run();
        });

        btnCancelar.addActionListener(ev -> dialog.dispose());

        panelBotonesDialog.add(btnAplicar);
        panelBotonesDialog.add(btnCancelar);
        dialog.add(panelBotonesDialog, BorderLayout.SOUTH);

        dialog.setVisible(true);
    });

    btnEliminar.addActionListener(e -> {
        int selectedRow = tablaUsuarios.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Seleccione un usuario para eliminar", "Advertencia", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String usuario = (String) tableModel.getValueAt(selectedRow, 0);
        int confirm = JOptionPane.showConfirmDialog(this, 
            "¿Está seguro de eliminar al usuario: " + usuario + "?",
            "Confirmar Eliminación", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            // En un sistema real, aquí ejecutarías: BURN USER username
            // Por ahora usaremos un comando personalizado
            String comando = String.format("-- Comando para eliminar usuario: %s", usuario);
            ejecutarConsultaConfiguracion(comando, "Usuario eliminado: " + usuario);
            cargarUsuarios.run();
        }
    });

    btnRefresh.addActionListener(e -> cargarUsuarios.run());

    return panel;
}

private JPanel crearPanelPrivilegios() {
    JPanel panel = new JPanel(new BorderLayout(15, 15));
    panel.setBackground(new Color(242, 242, 242));
    panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

    JPanel headerPanel = new JPanel(new BorderLayout());
    headerPanel.setBackground(Color.WHITE);
    headerPanel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 3, 0, new Color(108, 44, 120)),
        BorderFactory.createEmptyBorder(15, 20, 15, 20)
    ));

    JLabel titulo = new JLabel("🔐 CONTROL DE PRIVILEGIOS");
    titulo.setFont(new Font("Arial Black", Font.BOLD, 18));
    titulo.setForeground(new Color(108, 44, 120));

    headerPanel.add(titulo, BorderLayout.WEST);
    panel.add(headerPanel, BorderLayout.NORTH);

    JPanel contenido = new JPanel(new GridLayout(2, 1, 15, 15));
    contenido.setBackground(new Color(242, 242, 242));

    // Panel de gestión de privilegios por usuario
    JPanel panelGestion = new JPanel(new BorderLayout());
    panelGestion.setBackground(Color.WHITE);
    panelGestion.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
        BorderFactory.createEmptyBorder(15, 20, 15, 20)
    ));

    JLabel lblGestion = new JLabel("GESTIÓN DE PRIVILEGIOS POR USUARIO");
    lblGestion.setFont(new Font("Arial", Font.BOLD, 14));
    lblGestion.setForeground(new Color(108, 44, 120));
    panelGestion.add(lblGestion, BorderLayout.NORTH);

    // Formulario para otorgar/revocar privilegios
    JPanel panelForm = new JPanel(new GridLayout(3, 2, 10, 10));
    panelForm.setBackground(Color.WHITE);

    JComboBox<String> cmbUsuarios = new JComboBox<>(new String[]{"admin", "linares", "TEST", "usuario1", "usuario2"});
    JComboBox<String> cmbPrivilegio = new JComboBox<>(new String[]{"BRING", "FILE", "REWRITE", "BURN", "SUMMON", "SWEEP", "EMPOWER", "DISARM"});
    JComboBox<String> cmbAccion = new JComboBox<>(new String[]{"OTORGAR", "REVOCAR"});

    panelForm.add(new JLabel("Usuario:"));
    panelForm.add(cmbUsuarios);
    panelForm.add(new JLabel("Privilegio:"));
    panelForm.add(cmbPrivilegio);
    panelForm.add(new JLabel("Acción:"));
    panelForm.add(cmbAccion);

    panelGestion.add(panelForm, BorderLayout.CENTER);

    JButton btnEjecutar = crearBotonRobusto("⚡ Ejecutar Acción", new Color(108, 44, 120));
    btnEjecutar.addActionListener(e -> {
        String usuario = (String) cmbUsuarios.getSelectedItem();
        String privilegio = (String) cmbPrivilegio.getSelectedItem();
        String accion = (String) cmbAccion.getSelectedItem();

        String comando;
        String mensaje;

        if ("OTORGAR".equals(accion)) {
            comando = String.format("EMPOWER %s {%s}", usuario, privilegio);
            mensaje = String.format("Privilegio %s otorgado a %s", privilegio, usuario);
        } else {
            comando = String.format("DISARM %s {%s}", usuario, privilegio);
            mensaje = String.format("Privilegio %s revocado de %s", privilegio, usuario);
        }

        ejecutarConsultaConfiguracion(comando, mensaje);
    });

    JPanel panelBoton = new JPanel(new FlowLayout());
    panelBoton.setBackground(Color.WHITE);
    panelBoton.add(btnEjecutar);
    panelGestion.add(panelBoton, BorderLayout.SOUTH);

    // Panel de información de privilegios
    JPanel panelInfo = new JPanel(new BorderLayout());
    panelInfo.setBackground(Color.WHITE);
    panelInfo.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
        BorderFactory.createEmptyBorder(15, 20, 15, 20)
    ));

    JLabel lblInfo = new JLabel("INFORMACIÓN DE PRIVILEGIOS");
    lblInfo.setFont(new Font("Arial", Font.BOLD, 14));
    lblInfo.setForeground(new Color(108, 44, 120));
    panelInfo.add(lblInfo, BorderLayout.NORTH);

    JTextArea txtInfo = new JTextArea();
    txtInfo.setEditable(false);
    txtInfo.setFont(new Font("Monospaced", Font.PLAIN, 12));
    txtInfo.setText(
        "📋 PRIVILEGIOS DISPONIBLES:\n\n" +
        "• BRING    - Permite ejecutar BRING (consultas)\n" +
        "• FILE    - Permite ejecutar FILE (inserciones)\n" +
        "• REWRITE    - Permite ejecutar REWRITE (actualizaciones)\n" +
        "• SWEEP    - Permite ejecutar SWEEP (eliminaciones)\n" +
        "• SUMMON    - Permite ejecutar SUMMON (creaciones)\n" +
        "• BURN      - Permite ejecutar BURN (eliminaciones)\n" +
        "• EMPOWER     - Permite otorgar/revocar privilegios\n" +
        "• DISARM    - Permite revocar privilegios\n\n" +
        "👥 ROLES PREDEFINIDOS:\n\n" +
        "• READER    - BRING\n" +
        "• WRITER    - BRING, FILE, REWRITE\n" +
        "• MANAGER   - BRING, FILE, REWRITE, SWEEP, SUMMON, BURN\n" +
        "• ADMIN     - Todos los privilegios + EMPOWER, DISARM"
    );
    txtInfo.setBackground(new Color(250, 250, 250));

    panelInfo.add(new JScrollPane(txtInfo), BorderLayout.CENTER);

    contenido.add(panelGestion);
    contenido.add(panelInfo);

    panel.add(contenido, BorderLayout.CENTER);

    return panel;
}

// Método auxiliar para ejecutar consultas de configuración
private void ejecutarConsultaConfiguracion(String comando, String mensajeExito) {
    try {
        // Ejecutar el comando directamente
        ejecutarConsulta(comando);
        
        if (mensajeExito != null) {
            // Mostrar mensaje de éxito en la consola principal
            logMessage("[CONFIGURACIÓN] " + mensajeExito, Color.GREEN);
        }
        
    } catch (Exception ex) {
        logError("[CONFIGURACIÓN] Error: " + ex.getMessage());
    }
}

    private JButton crearBotonRobusto(String texto, Color color) {
        JButton btn = new JButton(texto);
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Arial", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        btn.addMouseListener(new MouseAdapter() {
            Color originalColor = color;

            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(color.brighter());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(originalColor);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                btn.setBackground(color.darker());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                btn.setBackground(color.brighter());
            }
        });

        return btn;
    }

    private void abrirSacredScroll() {
        JDialog dialog = new JDialog(this, "⚠️ ADVERTENCIA ⚠️", true);
        dialog.setSize(400, 200);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setResizable(false);

        JPanel panelMensaje = new JPanel();
        panelMensaje.setLayout(new BoxLayout(panelMensaje, BoxLayout.Y_AXIS));
        panelMensaje.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

        JLabel lblPregunta = new JLabel("¿Estás seguro que quieres");
        lblPregunta.setFont(new Font("Arial", Font.BOLD, 16));
        lblPregunta.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblCaos = new JLabel("desatar todo el caos?");
        lblCaos.setFont(new Font("Arial", Font.BOLD, 16));
        lblCaos.setForeground(new Color(139, 0, 0));
        lblCaos.setAlignmentX(Component.CENTER_ALIGNMENT);

        panelMensaje.add(lblPregunta);
        panelMensaje.add(Box.createVerticalStrut(5));
        panelMensaje.add(lblCaos);

        dialog.add(panelMensaje, BorderLayout.CENTER);

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        panelBotones.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));

        JButton btnSi = new JButton("SÍ");
        btnSi.setPreferredSize(new Dimension(100, 40));
        btnSi.setFont(new Font("Arial", Font.BOLD, 14));
        btnSi.setBackground(new Color(34, 139, 34));
        btnSi.setForeground(Color.WHITE);
        btnSi.setFocusPainted(false);
        btnSi.setBorderPainted(false);
        btnSi.setOpaque(true);

        JButton btnNo = new JButton("NO");
        btnNo.setPreferredSize(new Dimension(100, 40));
        btnNo.setFont(new Font("Arial", Font.BOLD, 14));
        btnNo.setBackground(new Color(220, 20, 60));
        btnNo.setForeground(Color.WHITE);
        btnNo.setFocusPainted(false);
        btnNo.setBorderPainted(false);
        btnNo.setOpaque(true);

        btnSi.addActionListener(e -> {
            dialog.dispose();
            try {
                File pdfFile = new File(getClass().getResource("/MythQL DDL.pdf").toURI());
                if (pdfFile.exists() && Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(pdfFile);
                    logMessage("Sacred Scroll abierto - ¡El caos ha sido desatado!", Color.CYAN);
                } else {
                    logError("No se pudo abrir el Sacred Scroll");
                }
            } catch (URISyntaxException | java.io.IOException ex) {
                logError("Error al abrir PDF: " + ex.getMessage());
            }
        });

        btnNo.addActionListener(e -> {
            dialog.dispose();
            logMessage("Has decidido no desatar el caos... por ahora.", Color.YELLOW);
        });

        btnSi.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btnSi.setBackground(new Color(50, 205, 50));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btnSi.setBackground(new Color(34, 139, 34));
            }
        });

        btnNo.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btnNo.setBackground(new Color(255, 69, 96));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btnNo.setBackground(new Color(220, 20, 60));
            }
        });

        panelBotones.add(btnSi);
        panelBotones.add(btnNo);

        dialog.add(panelBotones, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private JTextPane getCurrentTextPanel() {
        JScrollPane scroll = (JScrollPane) tabs.getSelectedComponent();
        JViewport viewport = scroll.getViewport();
        return (JTextPane) viewport.getView();
    }

    private void openThemeSelector() {
        JDialog dialog = new JDialog(this, "Seleccionar Tema", true);
        dialog.setSize(350, 300);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setResizable(false);

        JPanel previewPanel = new JPanel();
        previewPanel.setPreferredSize(new Dimension(300, 100));
        dialog.add(previewPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new GridLayout(0, 1, 10, 10));
        dialog.add(buttonPanel, BorderLayout.CENTER);

        String[] temas = {"Oscuro", "Claro", "Verde", "Azul"};

        for (String nombre : temas) {
            JButton btnTema = new JButton(nombre);

            btnTema.addActionListener(e -> {
                try {
                    String path = "/themes/" + nombre + ".theme";
                    Theme theme = Theme.loadFromResource(path);
                    aplicarTema(theme);
                    dialog.dispose();
                    logMessage("Tema cambiado a: " + nombre, Color.CYAN);
                } catch (Exception ex) {
                    logError("Error cargando tema: " + ex.getMessage());
                    ex.printStackTrace();
                }
            });

            btnTema.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    try {
                        Theme theme = Theme.loadFromResource("/themes/" + nombre + ".theme");
                        previewPanel.setBackground(theme.topPanel);
                    } catch (Exception ignored) {}
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    previewPanel.setBackground(dialog.getBackground());
                }
            });

            buttonPanel.add(btnTema);
        }

        dialog.setVisible(true);
    }

    private void aplicarTema(Theme theme) {
        topPanel.setBackground(theme.topPanel);
        leftPanel.setBackground(theme.leftPanel);
        bottomPanel.setBackground(theme.bottomPanel);
        consolePane.setBackground(theme.consoleBg);
        consolePane.setForeground(theme.consoleFg);

        this.currentTheme = theme;
    }
    
    private void cargarCategoriasKeywords(String resourcePath) {
        categoriasKeywords.clear();
        try (InputStream input = getClass().getResourceAsStream(resourcePath)) {
            if (input == null) {
                logError("No se encontró el archivo de categorías: " + resourcePath);
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String linea;
            while ((linea = reader.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("#")) continue;

                String[] partes = linea.split("=");
                if (partes.length != 2) continue;

                String categoria = partes[0].trim();
                String[] palabras = partes[1].split(",");
                List<String> lista = new ArrayList<>();
                for (String p : palabras) lista.add(p.trim().toUpperCase());

                categoriasKeywords.put(categoria, lista);
            }

            logMessage("Categorías de keywords cargadas correctamente.", Color.CYAN);
        } catch (Exception e) {
            logError("Error cargando categorías de keywords: " + e.getMessage());
        }
    }
    
    private void resaltarKeywordsCompleto(JTextPane pane) {
        StyledDocument doc = pane.getStyledDocument();
        try {
            String text = doc.getText(0, doc.getLength());
            String upperText = text.toUpperCase();

            // Resetear color a normal
            SimpleAttributeSet normal = new SimpleAttributeSet();
            StyleConstants.setForeground(normal, Color.BLACK);
            doc.setCharacterAttributes(0, doc.getLength(), normal, true);

            // Aplicar colores según categorías
            for (Map.Entry<String, List<String>> entry : categoriasKeywords.entrySet()) {
                String categoria = entry.getKey();
                List<String> palabras = entry.getValue();

                Color color = currentTheme.keywordColors.getOrDefault(categoria, Color.BLUE);
                SimpleAttributeSet attr = new SimpleAttributeSet();
                StyleConstants.setForeground(attr, color);
                StyleConstants.setBold(attr, true);

                for (String kw : palabras) {
                    int index = 0;
                    while ((index = upperText.indexOf(kw, index)) >= 0) {
                        boolean isWord = true;
                        if (index > 0) {
                            char before = upperText.charAt(index - 1);
                            if (Character.isLetterOrDigit(before) || before == '_') {
                                isWord = false;
                            }
                        }
                        if (isWord && index + kw.length() < upperText.length()) {
                            char after = upperText.charAt(index + kw.length());
                            if (Character.isLetterOrDigit(after) || after == '_') {
                                isWord = false;
                            }
                        }
                        if (isWord) {
                            doc.setCharacterAttributes(index, kw.length(), attr, false);
                        }
                        index += kw.length();
                    }
                }
            }
        } catch (Exception e) {
            logError("Error resaltando keywords: " + e.getMessage());
        }
    }
    
    private void mostrarTablaBring(String respuesta) {
        try {
            // Separar encabezado y datos
            int indexDatos = respuesta.indexOf("||");
            if (indexDatos == -1) {
                JOptionPane.showMessageDialog(this, "Formato de BRING inválido.", "BRING", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Header: ejemplo → "MATERIA ID \ NOMBRE"
            String headerLine = respuesta.substring(0, indexDatos).trim();

            // Separar nombre de tabla del resto
            String[] partesHeader = headerLine.split("\\s+", 2); // separa "MATERIA" de "ID \ NOMBRE"
            String nombreTabla = partesHeader[0].trim();

            // Separar columnas (después del nombre de la tabla)
            String columnasStr = (partesHeader.length > 1) ? partesHeader[1] : "";
            String[] columnas = Arrays.stream(columnasStr.split("\\\\"))
                                      .map(String::trim)
                                      .filter(s -> !s.isEmpty())
                                      .toArray(String[]::new);

            // === Registros ===
            String registrosStr = respuesta.substring(indexDatos + 2).trim();
            String[] registros = registrosStr.split("\\\\"); // separa registros por '\'

            List<String[]> filas = new ArrayList<>();
            for (String registro : registros) {
                registro = registro.trim();
                if (registro.isEmpty()) continue;

                // Separar valores por '|'
                String[] valores = Arrays.stream(registro.split("\\|"))
                                         .map(String::trim)
                                         .toArray(String[]::new);

                // Asegurar que tenga la cantidad correcta
                if (valores.length < columnas.length) {
                    String[] tmp = new String[columnas.length];
                    System.arraycopy(valores, 0, tmp, 0, valores.length);
                    for (int i = valores.length; i < columnas.length; i++) tmp[i] = "NULL";
                    valores = tmp;
                }

                filas.add(valores);
            }

            String[][] data = filas.toArray(new String[0][]);

            JTable table = new JTable(data, columnas);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            table.setRowHeight(24);
            table.setFont(new Font("Monospaced", Font.PLAIN, 13));
            table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 13));

            // Zebra stripes
            table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                               boolean hasFocus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    if (!isSelected) {
                        c.setBackground(row % 2 == 0 ? new Color(245, 245, 245) : Color.WHITE);
                    }
                    return c;
                }
            });

            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setPreferredSize(new Dimension(700, 400));

            JDialog dialog = new JDialog(this, "Resultado BRING: " + nombreTabla, false);
            dialog.setLayout(new BorderLayout(10, 10));
            dialog.add(scrollPane, BorderLayout.CENTER);

            JLabel lblTitulo = new JLabel("Tabla: " + nombreTabla, SwingConstants.CENTER);
            lblTitulo.setFont(new Font("Arial Black", Font.BOLD, 16));
            dialog.add(lblTitulo, BorderLayout.NORTH);

            dialog.pack();
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);

        } catch (Exception e) {
            logError("Error mostrando resultado de BRING: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private abstract class SimpleDocumentListener implements DocumentListener {
        public abstract void update(DocumentEvent e);
        @Override
        public void insertUpdate(DocumentEvent e) { update(e); }
        @Override
        public void removeUpdate(DocumentEvent e) { update(e); }
        @Override
        public void changedUpdate(DocumentEvent e) { update(e); }
    }

    @Override
    public void dispose() {
        if (clienteNotificaciones != null) {
            clienteNotificaciones.detener();
            System.out.println("Conexión de notificaciones cerrada.");
        }
        super.dispose();
    }
}