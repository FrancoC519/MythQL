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
        JButton gearGuardar = new JButton("⚙");
        gearGuardar.setBackground(new Color(255, 204, 102));
        gearGuardar.addActionListener(e -> guardarQueryActual());
        topPanel.add(gearGuardar);

        for (int i = 1; i < 4; i++) {
            JButton gear = new JButton("⚙");
            gear.setBackground(new Color(255, 204, 102));
            topPanel.add(gear);
        }

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

    private void abrirSacredScroll() {
        try {
            File pdfFile = new File(getClass().getResource("/MythQL DDL.pdf").toURI());
            if (pdfFile.exists() && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(pdfFile);
                logMessage("Sacred Scroll abierto", Color.CYAN);
            } else {
                logError("No se pudo abrir el Sacred Scroll");
            }
        } catch (URISyntaxException | java.io.IOException ex) {
            logError("Error al abrir PDF: " + ex.getMessage());
        }
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
            // Separar encabezado y datos
            int indexDatos = respuesta.indexOf("||");
            if (indexDatos == -1) {
                JOptionPane.showMessageDialog(this, "Formato de BRING inválido.", "BRING", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Header
            String headerLine = respuesta.substring(0, indexDatos).trim(); // ej: "MATERIA ID | NOMBRE"
            String[] columnas = Arrays.stream(headerLine.split("\\|"))
                                      .map(String::trim) // solo trim, no quitar comillas
                                      .toArray(String[]::new);

            // Nombre de la tabla (primer token del header antes de espacio o |
            String nombreTabla = columnas[0].split(" ")[0];

            // Registros
            String registrosStr = respuesta.substring(indexDatos + 2); 
            String[] registros = registrosStr.split("\\\\"); // separa registros

            List<String[]> filas = new ArrayList<>();
            for (String registro : registros) {
                registro = registro.trim();
                if (registro.isEmpty()) continue;

                // Separar columnas por "|"
                String[] valores = Arrays.stream(registro.split("\\|"))
                                         .map(String::trim)
                                         .toArray(String[]::new);

                // Asegurarnos que la fila tenga la misma cantidad de columnas que el header
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