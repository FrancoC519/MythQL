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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.Timer;
import javax.swing.tree.*;
import java.util.function.Consumer;
import javax.swing.table.DefaultTableCellRenderer;

public class MythQL_UI extends JFrame {
    private JTabbedPane tabs;
    private JTextPane consolePane;
    private JPanel topPanel, leftPanel, bottomPanel;
    private String token;
    private String host;
    private int port;
    private String lastErrorMsg = "";
    private Timer highlightTimer;
    
    // Conexiones separadas
    private ClienteNotificaciones clienteNotificaciones;
    private ClienteConexion clienteConsultas;
    
    private JTree schemaTree;
    private DefaultTreeModel treeModel;

    public MythQL_UI(String token, String host, int port) {
        this.token = token;
        this.host = host;
        this.port = port;
        this.clienteConsultas = new ClienteConexion(host, port);
        initializeUI();
        
        // Configurar notificaciones DESPUÉS de que la UI esté lista
        SwingUtilities.invokeLater(() -> {
            configurarNotificaciones();
            cargarEsquemasJerarquicos();
        });
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

        topPanel.add(btnExecute);
        topPanel.add(btnExecuteSel);
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

        // ------------------- Tabs -------------------
        tabs = new JTabbedPane();
        for (int i = 1; i <= 3; i++) {
            JTextPane queryPane = new JTextPane();
            queryPane.setFont(new Font("Monospaced", Font.PLAIN, 14));
            JScrollPane scroll = new JScrollPane(queryPane);
            tabs.add("QUERY " + i, scroll);

            queryPane.getDocument().addDocumentListener(new SimpleDocumentListener() {
                @Override
                public void update(DocumentEvent e) {
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
                        if (respuestaServidor.startsWith("RESULT ERROR")) {
                            logMessage(respuestaServidor, Color.RED);
                        } else {
                            if (comando.toUpperCase().startsWith("BRING")) {
                                mostrarTablaBring(respuestaServidor);
                            } else {
                                logMessage(respuestaServidor, Color.GREEN);
                            }

                            if (comando.toUpperCase().startsWith("SUMMON") || 
                                comando.toUpperCase().startsWith("BURN") ||
                                comando.toUpperCase().startsWith("UTILIZE")) {
                                cargarEsquemasJerarquicos();
                            }
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
        JTextPane area = getCurrentTextPane();
        String contenido = area.getText().trim();

        if (contenido.isEmpty()) {
            logMessage("El query está vacío, nada que guardar.", Color.YELLOW);
            return;
        }

        int index = tabs.getSelectedIndex() + 1;
        String nombreArchivo = "Script" + index + ".mql";

        try {
            Path path = Paths.get(nombreArchivo);
            Files.write(path, contenido.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            logMessage("Query guardado en " + nombreArchivo, Color.GREEN);
        } catch (Exception ex) {
            logError("Error al guardar el archivo: " + ex.getMessage());
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

                JTextPane area = getCurrentTextPane();
                area.setText(contenido);

                resaltarKeywordsCompleto(area);

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
        JPanel panelPuertos = crearPanelPuertos();
        JPanel panelComandos = crearPanelComandos();
        JPanel panelSeguridad = crearPanelSeguridad();
        JPanel panelRespaldos = crearPanelRespaldos();

        panelContenido.add(panelUsuarios, "Usuarios");
        panelContenido.add(panelPrivilegios, "Privilegios");
        panelContenido.add(panelPuertos, "Puertos");
        panelContenido.add(panelComandos, "Comandos");
        panelContenido.add(panelSeguridad, "Seguridad");
        panelContenido.add(panelRespaldos, "Respaldos");

        CardLayout cardLayout = (CardLayout) panelContenido.getLayout();

        String[] opciones = {"Usuarios", "Privilegios", "Puertos", "Comandos", "Seguridad", "Respaldos"};
        String[] iconos = {"👤⚙", "🔐⚙", "🔌⚙", "⚙", "🛡⚙", "💾⚙"};

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

        JPanel contenido = new JPanel(new BorderLayout(15, 15));
        contenido.setBackground(Color.WHITE);
        contenido.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));

        String[] columnNames = {"Usuario", "Rol", "Estado", "Último Acceso"};
        Object[][] data = {
            {"admin", "Administrador", "Activo", "2025-10-21 14:30"},
            {"user1", "Usuario Estándar", "Activo", "2025-10-21 10:15"},
            {"user2", "Usuario Estándar", "Inactivo", "2025-10-18 16:45"},
            {"guest", "Invitado", "Activo", "2025-10-21 09:00"}
        };

        JTable tablaUsuarios = new JTable(data, columnNames);
        tablaUsuarios.setRowHeight(30);
        tablaUsuarios.setFont(new Font("Arial", Font.PLAIN, 13));
        tablaUsuarios.getTableHeader().setFont(new Font("Arial", Font.BOLD, 13));
        tablaUsuarios.getTableHeader().setBackground(new Color(108, 44, 120));
        tablaUsuarios.getTableHeader().setForeground(Color.WHITE);
        tablaUsuarios.setSelectionBackground(new Color(200, 180, 210));

        JScrollPane scrollTabla = new JScrollPane(tablaUsuarios);
        scrollTabla.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180)));

        contenido.add(scrollTabla, BorderLayout.CENTER);

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        panelBotones.setBackground(Color.WHITE);

        JButton btnAgregar = crearBotonRobusto("⚙ Agregar Usuario", new Color(34, 139, 34));
        JButton btnModificar = crearBotonRobusto("⚙️ Modificar", new Color(30, 144, 255));
        JButton btnEliminar = crearBotonRobusto("🗑⚙ Eliminar", new Color(220, 20, 60));
        JButton btnRefresh = crearBotonRobusto("⚙ Actualizar", new Color(108, 44, 120));

        panelBotones.add(btnAgregar);
        panelBotones.add(btnModificar);
        panelBotones.add(btnEliminar);
        panelBotones.add(btnRefresh);

        contenido.add(panelBotones, BorderLayout.SOUTH);
        panel.add(contenido, BorderLayout.CENTER);

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

        JPanel panelBasicos = crearSeccionPrivilegios("PERMISOS BÁSICOS", new String[]{
            "⚙ Permiso de Lectura (BRING)",
            "⚙ Permiso de Escritura (MANIFEST)",
            "⚙ Permiso de Actualización (UPDATE)",
            "⚙ Permiso de Consulta (SELECT)"
        });

        JPanel panelAvanzados = crearSeccionPrivilegios("PERMISOS AVANZADOS", new String[]{
            "⚙ Creación de Bases de Datos (SUMMON DATABASE)",
            "⚙ Eliminación de Objetos (BURN)",
            "⚙ Gestión de Usuarios",
            "⚙ Privilegios de Administrador Total"
        });

        contenido.add(panelBasicos);
        contenido.add(panelAvanzados);

        panel.add(contenido, BorderLayout.CENTER);

        JPanel panelBoton = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panelBoton.setBackground(new Color(242, 242, 242));
        panelBoton.add(crearBotonRobusto("💾⚙Aplicar Cambios", new Color(108, 44, 120)));

        panel.add(panelBoton, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel crearSeccionPrivilegios(String titulo, String[] opciones) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
            BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));

        JLabel lblTitulo = new JLabel(titulo);
        lblTitulo.setFont(new Font("Arial", Font.BOLD, 14));
        lblTitulo.setForeground(new Color(108, 44, 120));
        lblTitulo.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        panel.add(lblTitulo, BorderLayout.NORTH);

        JPanel checkPanel = new JPanel(new GridLayout(0, 1, 5, 8));
        checkPanel.setBackground(Color.WHITE);

        for (String opcion : opciones) {
            JCheckBox chk = new JCheckBox(opcion);
            chk.setFont(new Font("Arial", Font.PLAIN, 13));
            chk.setBackground(Color.WHITE);
            chk.setFocusPainted(false);
            checkPanel.add(chk);
        }

        panel.add(checkPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel crearPanelPuertos() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(new Color(242, 242, 242));
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 3, 0, new Color(108, 44, 120)),
            BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));

        JLabel titulo = new JLabel("🔌⚙ CONFIGURACIÓN DE RED Y PUERTOS");
        titulo.setFont(new Font("Arial Black", Font.BOLD, 18));
        titulo.setForeground(new Color(108, 44, 120));

        headerPanel.add(titulo, BorderLayout.WEST);
        panel.add(headerPanel, BorderLayout.NORTH);

        JPanel contenido = new JPanel();
        contenido.setLayout(new BoxLayout(contenido, BoxLayout.Y_AXIS));
        contenido.setBackground(Color.WHITE);
        contenido.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));

        contenido.add(crearCampoConfig("Puerto Principal:", "5000"));
        contenido.add(Box.createVerticalStrut(15));
        contenido.add(crearCampoConfig("Puerto de Notificaciones:", "5001"));
        contenido.add(Box.createVerticalStrut(15));
        contenido.add(crearCampoConfig("Host del Servidor:", "localhost"));
        contenido.add(Box.createVerticalStrut(15));
        contenido.add(crearCampoConfig("Timeout de Conexión (ms):", "5000"));
        contenido.add(Box.createVerticalStrut(15));
        contenido.add(crearCampoConfig("Máximo de Conexiones:", "100"));

        panel.add(contenido, BorderLayout.CENTER);

        // Botones
        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        panelBotones.setBackground(new Color(242, 242, 242));
        panelBotones.add(crearBotonRobusto("🔄⚙Reiniciar Servidor", new Color(255, 140, 0)));
        panelBotones.add(crearBotonRobusto("💾⚙Guardar Configuración", new Color(34, 139, 34)));

        panel.add(panelBotones, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel crearCampoConfig(String etiqueta, String valorDefault) {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setBackground(Color.WHITE);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel lbl = new JLabel(etiqueta);
        lbl.setFont(new Font("Arial", Font.BOLD, 13));
        lbl.setPreferredSize(new Dimension(250, 30));

        JTextField txt = new JTextField(valorDefault);
        txt.setFont(new Font("Arial", Font.PLAIN, 13));
        txt.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));

        panel.add(lbl, BorderLayout.WEST);
        panel.add(txt, BorderLayout.CENTER);

        return panel;
    }

    private JPanel crearPanelComandos() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(new Color(242, 242, 242));
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 3, 0, new Color(108, 44, 120)),
            BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));

        JLabel titulo = new JLabel("⚙ PERSONALIZACIÓN DE COMANDOS");
        titulo.setFont(new Font("Arial Black", Font.BOLD, 18));
        titulo.setForeground(new Color(108, 44, 120));

        headerPanel.add(titulo, BorderLayout.WEST);
        panel.add(headerPanel, BorderLayout.NORTH);

        JPanel contenido = new JPanel(new BorderLayout(15, 15));
        contenido.setBackground(Color.WHITE);
        contenido.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));

        String[] columnNames = {"Comando Original", "Alias", "Descripción", "Estado"};
        Object[][] data = {
            {"SUMMON", "CREATE", "Crear base de datos/tabla", "Activo"},
            {"BURN", "DROP", "Eliminar objetos", "Activo"},
            {"BRING", "SELECT", "Consultar datos", "Activo"},
            {"MANIFEST", "INSERT", "Insertar registros", "Activo"},
            {"UTILIZE", "USE", "Seleccionar base de datos", "Activo"}
        };

        JTable tablaComandos = new JTable(data, columnNames);
        tablaComandos.setRowHeight(30);
        tablaComandos.setFont(new Font("Monospaced", Font.PLAIN, 12));
        tablaComandos.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        tablaComandos.getTableHeader().setBackground(new Color(108, 44, 120));
        tablaComandos.getTableHeader().setForeground(Color.WHITE);

        JScrollPane scrollTabla = new JScrollPane(tablaComandos);
        scrollTabla.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180)));

        contenido.add(scrollTabla, BorderLayout.CENTER);

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        panelBotones.setBackground(Color.WHITE);
        panelBotones.add(crearBotonRobusto("✏️ Editar Alias", new Color(30, 144, 255)));
        panelBotones.add(crearBotonRobusto("🔄 Restaurar Predeterminados", new Color(255, 140, 0)));

        contenido.add(panelBotones, BorderLayout.SOUTH);
        panel.add(contenido, BorderLayout.CENTER);

        return panel;
    }

    private JPanel crearPanelSeguridad() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(new Color(242, 242, 242));
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 3, 0, new Color(139, 0, 0)),
            BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));

        JLabel titulo = new JLabel("🛡⚙ CONFIGURACIÓN DE SEGURIDAD");
        titulo.setFont(new Font("Arial Black", Font.BOLD, 18));
        titulo.setForeground(new Color(139, 0, 0));

        headerPanel.add(titulo, BorderLayout.WEST);
        panel.add(headerPanel, BorderLayout.NORTH);

        JPanel contenido = new JPanel(new GridLayout(2, 2, 15, 15));
        contenido.setBackground(new Color(242, 242, 242));

        contenido.add(crearTarjetaSeguridad("🔒⚙ Encriptación", new String[]{
            "SSL/TLS Habilitado",
            "Encriptación de Datos",
            "Certificados Válidos"
        }));

        contenido.add(crearTarjetaSeguridad("📝⚙ Auditoría", new String[]{
            "Log de Consultas",
            "Log de Accesos",
            "Log de Errores"
        }));

        contenido.add(crearTarjetaSeguridad("🔑⚙ Autenticación", new String[]{
            "Autenticación 2FA",
            "Tokens de Sesión",
            "Expiración Automática"
        }));

        contenido.add(crearTarjetaSeguridad("⚙ Protección", new String[]{
            "Anti-Fuerza Bruta",
            "Límite de Intentos",
            "Bloqueo Temporal"
        }));

        panel.add(contenido, BorderLayout.CENTER);

        JPanel panelBoton = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panelBoton.setBackground(new Color(242, 242, 242));
        panelBoton.add(crearBotonRobusto("💾⚙ Guardar Configuración de Seguridad", new Color(139, 0, 0)));

        panel.add(panelBoton, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel crearTarjetaSeguridad(String titulo, String[] opciones) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 2),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        JLabel lblTitulo = new JLabel(titulo);
        lblTitulo.setFont(new Font("Arial", Font.BOLD, 14));
        lblTitulo.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        panel.add(lblTitulo, BorderLayout.NORTH);

        JPanel checkPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        checkPanel.setBackground(Color.WHITE);

        for (String opcion : opciones) {
            JCheckBox chk = new JCheckBox(opcion);
            chk.setFont(new Font("Arial", Font.PLAIN, 12));
            chk.setBackground(Color.WHITE);
            chk.setFocusPainted(false);
            checkPanel.add(chk);
        }

        panel.add(checkPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel crearPanelRespaldos() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(new Color(242, 242, 242));
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 3, 0, new Color(108, 44, 120)),
            BorderFactory.createEmptyBorder(15, 20, 15, 20)
        ));

        JLabel titulo = new JLabel("💾⚙ RESPALDOS Y RECUPERACIÓN");
        titulo.setFont(new Font("Arial Black", Font.BOLD, 18));
        titulo.setForeground(new Color(108, 44, 120));

        headerPanel.add(titulo, BorderLayout.WEST);
        panel.add(headerPanel, BorderLayout.NORTH);

        JPanel contenidoPrincipal = new JPanel(new BorderLayout(15, 15));
        contenidoPrincipal.setBackground(new Color(242, 242, 242));

        JPanel panelConfiguracion = new JPanel();
        panelConfiguracion.setLayout(new BoxLayout(panelConfiguracion, BoxLayout.Y_AXIS));
        panelConfiguracion.setBackground(Color.WHITE);
        panelConfiguracion.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                "Configuración de Respaldos Automáticos",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 13),
                new Color(108, 44, 120)
            ),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        JPanel panelFrecuencia = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panelFrecuencia.setBackground(Color.WHITE);
        panelFrecuencia.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel lblFrecuencia = new JLabel("Frecuencia:");
        lblFrecuencia.setFont(new Font("Arial", Font.BOLD, 13));

        String[] frecuencias = {"Manual", "Cada Hora", "Diario", "Semanal", "Mensual"};
        JComboBox<String> comboFrecuencia = new JComboBox<>(frecuencias);
        comboFrecuencia.setFont(new Font("Arial", Font.PLAIN, 13));
        comboFrecuencia.setPreferredSize(new Dimension(200, 30));

        panelFrecuencia.add(lblFrecuencia);
        panelFrecuencia.add(comboFrecuencia);

        JPanel panelRuta = new JPanel(new BorderLayout(10, 0));
        panelRuta.setBackground(Color.WHITE);
        panelRuta.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel lblRuta = new JLabel("Ruta de Respaldos:");
        lblRuta.setFont(new Font("Arial", Font.BOLD, 13));
        lblRuta.setPreferredSize(new Dimension(150, 30));

        JTextField txtRuta = new JTextField("C:/MythQL/Backups/");
        txtRuta.setFont(new Font("Arial", Font.PLAIN, 13));
        txtRuta.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));

        JButton btnExplorar = new JButton("📁 Explorar");
        btnExplorar.setFont(new Font("Arial", Font.PLAIN, 12));
        btnExplorar.setPreferredSize(new Dimension(100, 30));
        btnExplorar.setFocusPainted(false);

        panelRuta.add(lblRuta, BorderLayout.WEST);
        panelRuta.add(txtRuta, BorderLayout.CENTER);
        panelRuta.add(btnExplorar, BorderLayout.EAST);

        panelConfiguracion.add(panelFrecuencia);
        panelConfiguracion.add(Box.createVerticalStrut(10));
        panelConfiguracion.add(panelRuta);

        JPanel panelHistorial = new JPanel(new BorderLayout(10, 10));
        panelHistorial.setBackground(Color.WHITE);
        panelHistorial.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                "Historial de Respaldos",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 13),
                new Color(108, 44, 120)
            ),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        String[] columnNames = {"Fecha", "Hora", "Tamaño", "Base de Datos", "Estado"};
        Object[][] data = {
            {"2025-10-21", "14:30:00", "245 MB", "MythDB_Principal", "✓ Exitoso"},
            {"2025-10-21", "12:00:00", "198 MB", "MythDB_Principal", "✓ Exitoso"},
            {"2025-10-20", "14:30:00", "240 MB", "MythDB_Principal", "✓ Exitoso"},
            {"2025-10-19", "14:30:00", "235 MB", "MythDB_Principal", "✓ Exitoso"},
            {"2025-10-18", "14:30:00", "230 MB", "MythDB_Principal", "⚠ Advertencia"}
        };

        JTable tablaRespaldos = new JTable(data, columnNames);
        tablaRespaldos.setRowHeight(28);
        tablaRespaldos.setFont(new Font("Monospaced", Font.PLAIN, 12));
        tablaRespaldos.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        tablaRespaldos.getTableHeader().setBackground(new Color(108, 44, 120));
        tablaRespaldos.getTableHeader().setForeground(Color.WHITE);
        tablaRespaldos.setSelectionBackground(new Color(200, 180, 210));

        JScrollPane scrollTabla = new JScrollPane(tablaRespaldos);
        scrollTabla.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180)));
        scrollTabla.setPreferredSize(new Dimension(0, 150));

        panelHistorial.add(scrollTabla, BorderLayout.CENTER);

        contenidoPrincipal.add(panelConfiguracion, BorderLayout.NORTH);
        contenidoPrincipal.add(panelHistorial, BorderLayout.CENTER);

        panel.add(contenidoPrincipal, BorderLayout.CENTER);

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        panelBotones.setBackground(new Color(242, 242, 242));

        JButton btnCrearBackup = crearBotonRobusto("💾⚙Crear Respaldo Ahora", new Color(34, 139, 34));
        JButton btnRestaurar = crearBotonRobusto("⚙Restaurar desde Respaldo", new Color(255, 140, 0));
        JButton btnEliminarBackup = crearBotonRobusto("🗑⚙Eliminar Respaldo", new Color(220, 20, 60));

        panelBotones.add(btnCrearBackup);
        panelBotones.add(btnRestaurar);
        panelBotones.add(btnEliminarBackup);

        panel.add(panelBotones, BorderLayout.SOUTH);

        return panel;
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

    private JTextPane getCurrentTextPane() {
        JScrollPane scroll = (JScrollPane) tabs.getSelectedComponent();
        JViewport viewport = scroll.getViewport();
        return (JTextPane) viewport.getView();
    }

    private void openThemeSelector() {
        JDialog dialog = new JDialog(this, "Seleccionar Tema", true);
        dialog.setSize(350, 300);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel previewPanel = new JPanel();
        previewPanel.setPreferredSize(new Dimension(300, 100));
        dialog.add(previewPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new GridLayout(0, 1, 10, 10));
        dialog.add(buttonPanel, BorderLayout.CENTER);

        Object[][] temas = {
                {"Oscuro", new Color(108, 44, 120), new Color(242, 242, 242), Color.BLACK, Color.WHITE},
                {"Claro", new Color(230, 242, 255), Color.WHITE, Color.WHITE, Color.BLACK},
                {"Verde", new Color(0, 102, 51), new Color(200, 255, 200), Color.BLACK, Color.WHITE},
                {"Azul", new Color(0, 51, 102), new Color(200, 220, 255), Color.WHITE, Color.BLACK}
        };

        for (Object[] tema : temas) {
            String nombre = (String) tema[0];
            Color topColor = (Color) tema[1];
            Color leftColor = (Color) tema[2];
            Color consoleBg = (Color) tema[3];
            Color consoleFg = (Color) tema[4];

            JButton btnTema = new JButton(nombre);
            btnTema.addActionListener(e -> {
                topPanel.setBackground(topColor);
                leftPanel.setBackground(leftColor);
                bottomPanel.setBackground(leftColor);
                consolePane.setBackground(consoleBg);
                consolePane.setForeground(consoleFg);
                dialog.dispose();
                logMessage("Tema cambiado a: " + nombre, Color.CYAN);
            });

            btnTema.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    previewPanel.setBackground(topColor);
                    previewPanel.setForeground(consoleFg);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    previewPanel.setBackground(dialog.getBackground());
                    previewPanel.setForeground(Color.BLACK);
                }
            });

            buttonPanel.add(btnTema);
        }

        dialog.setVisible(true);
    }

    private void resaltarKeywordsCompleto(JTextPane pane) {
        StyledDocument doc = pane.getStyledDocument();
        try {
            String text = doc.getText(0, doc.getLength());
            
            SimpleAttributeSet normal = new SimpleAttributeSet();
            StyleConstants.setForeground(normal, Color.BLACK);
            doc.setCharacterAttributes(0, doc.getLength(), normal, true);

            List<String> keywords = Arrays.asList("FILE","SUMMON", "DATABASE", "TABLE", "BURN", "BRING", "UTILIZE", "LOGOUT","TABLES","INT","VARCHAR","MANIFEST","DATABASES","DEPICT","SELF","STACKABLE");
            String upperText = text.toUpperCase();
            
            for (String kw : keywords) {
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
                        SimpleAttributeSet attr = new SimpleAttributeSet();
                        StyleConstants.setForeground(attr, Color.BLUE);
                        StyleConstants.setBold(attr, true);
                        doc.setCharacterAttributes(index, kw.length(), attr, false);
                    }
                    index += kw.length();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void mostrarTablaBring(String respuesta) {
        try {
            int indexDatos = respuesta.indexOf("||");
            if (indexDatos == -1) {
                JOptionPane.showMessageDialog(this, "Formato de BRING inválido.", "BRING", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String headerLine = respuesta.substring(0, indexDatos).trim();
            String[] columnas = Arrays.stream(headerLine.split("\\|"))
                                      .map(String::trim)
                                      .toArray(String[]::new);

            String nombreTabla = columnas[0].split(" ")[0];

            String registrosStr = respuesta.substring(indexDatos + 2); 
            String[] registros = registrosStr.split("\\\\");

            List<String[]> filas = new ArrayList<>();
            for (String registro : registros) {
                registro = registro.trim();
                if (registro.isEmpty()) continue;

                String[] valores = Arrays.stream(registro.split("\\|"))
                                         .map(String::trim)
                                         .toArray(String[]::new);

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
