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
import javax.swing.text.Element;

public class MythQL_UI extends JFrame {
    private JTabbedPane tabs;
    private JTextPane consolePane;
    private JPanel topPanel, leftPanel, bottomPanel;
    private String token;
    private String host;
    private int port;
    private String lastErrorMsg = "";
    private Timer highlightTimer;
    private ClienteConexion conexionNotificaciones;
    private JTree schemaTree;
    private DefaultTreeModel treeModel;

    public MythQL_UI(String token, String host, int port) {
        this.token = token;
        this.host = host;
        this.port = port;
        initializeUI();

        // Configurar notificaciones DESPUÉS de que la UI esté lista
        SwingUtilities.invokeLater(() -> {
            configurarNotificaciones(token, host, port);
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
                    Element root2 = currentPane.getDocument().getDefaultRootElement();
                    int line = root2.getElementIndex(caret);
                    int start = root2.getElement(line).getStartOffset();
                    int end = root2.getElement(line).getEndOffset();
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
                    ejecutarConsulta("LOGOUT " + token);
                } catch (Exception ex) {
                    System.out.println("Error enviando logout: " + ex.getMessage());
                }
                if (conexionNotificaciones != null) {
                    conexionNotificaciones.detenerEscucha();
                }
            }
        });
    }

    // NUEVO: Cargar esquemas jerárquicos
    private void cargarEsquemasJerarquicos() {
        try {
            ClienteConexion conn = new ClienteConexion(host, port);
            String respuesta = conn.obtenerEsquemas(token);
            
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

    // NUEVO: Actualizar árbol con datos jerárquicos
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

    private void configurarNotificaciones(String token, String host, int port) {
        try {
            conexionNotificaciones = new ClienteConexion(host, port);
            String respuesta = conexionNotificaciones.suscribirANotificaciones(token);
            
            if (respuesta.startsWith("OK")) {
                conexionNotificaciones.iniciarEscuchaNotificaciones(this::mostrarNotificacion);
                logMessage("Suscripción a notificaciones activada", Color.CYAN);
            } else {
                logError("No se pudo suscribir a notificaciones: " + respuesta);
            }
        } catch (Exception e) {
            logError("Error configurando notificaciones: " + e.getMessage());
        }
    }

    private void mostrarNotificacion(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            logMessage("🔔 " + mensaje, Color.ORANGE);
            
            // Actualizar esquemas cuando hay cambios
            if (mensaje.contains("creada") || mensaje.contains("eliminada") || 
                mensaje.contains("insertados") || mensaje.contains("cambió base")) {
                cargarEsquemasJerarquicos();
            }
            
            // Opcional: Mostrar notificación emergente para cambios importantes
            if (mensaje.contains("creada") || mensaje.contains("eliminada")) {
                JOptionPane.showMessageDialog(this, 
                    mensaje, 
                    "Nueva Notificación", 
                    JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }

    // Resto de los métodos existentes (ejecutarConsulta, logMessage, etc.)
    // ... (mantener todos los métodos existentes igual)
    
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
                        ClienteConexion conexion = new ClienteConexion(host, port);
                        String respuestaServidor = conexion.enviarConsultaConToken(token, comando);

                        logMessageWithoutEnter("Respuesta del servidor: ", Color.WHITE);
                        if (respuestaServidor.startsWith("RESULT ERROR")) {
                            logMessage(respuestaServidor, Color.RED);
                        } else {
                            logMessage(respuestaServidor, Color.GREEN);
                            
                            // Actualizar esquemas si fue un comando que cambia estructura
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
        JFrame wizardFrame = new JFrame("Wizard");
        wizardFrame.setSize(500, 200);
        wizardFrame.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel errorLabel = new JLabel(errorMsg);
        errorLabel.setForeground(Color.RED);
        errorLabel.setFont(new Font("Arial", Font.PLAIN, 14));

        JLabel scrollLabel = new JLabel("   Consulte el Scroll");
        scrollLabel.setForeground(new Color(0, 102, 204));
        scrollLabel.setFont(new Font("Arial", Font.BOLD, 14));
        scrollLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        scrollLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                abrirSacredScroll();
            }
        });

        panel.add(errorLabel);
        panel.add(scrollLabel);
        wizardFrame.add(panel);
        wizardFrame.setVisible(true);
    }

    private void guardarQueryActual() {
        JTextPane area = getCurrentTextPane();
        String contenido = area.getText().trim();

        if (contenido.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El query está vacío, nada que guardar.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int index = tabs.getSelectedIndex() + 1;
        String nombreArchivo = "Script" + index + ".mql";

        try {
            Path path = Paths.get(nombreArchivo);
            Files.write(path, contenido.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            JOptionPane.showMessageDialog(this, "Query guardado en " + nombreArchivo,
                    "Guardado", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error al guardar el archivo: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void abrirSacredScroll() {
        JPanel panel = new JPanel(new BorderLayout(10,10));
        JLabel label = new JLabel("Are you sure you want to unleash chaos?", SwingConstants.CENTER);
        label.setFont(new Font("Arial", Font.BOLD, 16));
        panel.add(label, BorderLayout.CENTER);

        JButton btnYes = new JButton("Yes");
        JButton btnNo = new JButton("No");
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(btnYes);
        buttonPanel.add(btnNo);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        JDialog dialog = new JDialog(this, "Sacred Scroll", true);
        dialog.getContentPane().add(panel);
        dialog.setSize(400, 150);
        dialog.setLocationRelativeTo(this);

        btnYes.addActionListener(e -> {
            try {
                File pdfFile = new File(getClass().getResource("/MythQL DDL.pdf").toURI());
                if (pdfFile.exists() && Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(pdfFile);
                }
            } catch (URISyntaxException | java.io.IOException ex) {
                JOptionPane.showMessageDialog(this, "Error al abrir PDF: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
            dialog.dispose();
        });

        btnNo.addActionListener(e -> dialog.dispose());
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

    private abstract class SimpleDocumentListener implements DocumentListener {
        public abstract void update(DocumentEvent e);
        @Override
        public void insertUpdate(DocumentEvent e) { update(e); }
        @Override
        public void removeUpdate(DocumentEvent e) { update(e); }
        @Override
        public void changedUpdate(DocumentEvent e) { update(e); }
    }
}