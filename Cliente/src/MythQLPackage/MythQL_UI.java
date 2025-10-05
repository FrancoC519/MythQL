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
import java.util.Arrays;
import java.util.List;
import javax.swing.Timer;

public class MythQL_UI extends JFrame {
    private JTabbedPane tabs;
    private JTextPane consolePane;
    private JPanel topPanel, leftPanel, bottomPanel;
    private String token;
    private String lastErrorMsg = "";
    private Timer highlightTimer;

    public MythQL_UI(String token) {
        this.token = token;
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

        topPanel.add(btnExecute);
        topPanel.add(btnExecuteSel);
        topPanel.add(btnSacred);
        topPanel.add(btnTheme);

        // ------------------- Botones de engranaje -------------------
        JButton gearGuardar = new JButton("⚙"); // Primer engranaje = guardar query
        gearGuardar.setBackground(new Color(255, 204, 102));
        gearGuardar.addActionListener(e -> guardarQueryActual());
        topPanel.add(gearGuardar);

        for (int i = 1; i < 4; i++) {
            JButton gear = new JButton("⚙");
            gear.setBackground(new Color(255, 204, 102));
            topPanel.add(gear);
        }

        add(topPanel, BorderLayout.NORTH);

        // ------------------- Panel izquierdo -------------------
        leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setPreferredSize(new Dimension(220, 0));
        leftPanel.setBackground(new Color(242, 242, 242));

        JLabel lblManagement = new JLabel("MANAGEMENT");
        lblManagement.setFont(new Font("Arial", Font.BOLD, 12));
        leftPanel.add(lblManagement);

        String[] mgmtItems = {
                "Server Status", "Client Connections", "Users and Privileges",
                "Status and System Variables", "Data Export", "Data Import/Restore"
        };
        for (String item : mgmtItems) {
            JLabel opt = new JLabel(" - " + item);
            leftPanel.add(opt);
        }

        leftPanel.add(Box.createVerticalStrut(10));
        JLabel lblSchemas = new JLabel("SCHEMAS");
        lblSchemas.setFont(new Font("Arial", Font.BOLD, 12));
        leftPanel.add(lblSchemas);

        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("customer");
        model.addElement("film");
        model.addElement("language");
        JList<String> schemaList = new JList<>(model);
        JScrollPane schemaScroll = new JScrollPane(schemaList);
        leftPanel.add(schemaScroll);

        add(leftPanel, BorderLayout.WEST);

        // ------------------- Tabs -------------------
        tabs = new JTabbedPane();
        for (int i = 1; i <= 3; i++) {
            JTextPane queryPane = new JTextPane();
            queryPane.setFont(new Font("Monospaced", Font.PLAIN, 14));
            JScrollPane scroll = new JScrollPane(queryPane);
            tabs.add("QUERY " + i, scroll);

            // Listener para resaltar keywords optimizado con Timer
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
                    Element root = currentPane.getDocument().getDefaultRootElement();
                    int line = root.getElementIndex(caret);
                    int start = root.getElement(line).getStartOffset();
                    int end = root.getElement(line).getEndOffset();
                    sel = currentPane.getDocument().getText(start, end - start).trim();
                } catch (Exception ex) {
                    sel = "";
                }
            }
            ejecutarConsulta(sel);
        });

        btnSacred.addActionListener(e -> abrirSacredScroll());
        btnTheme.addActionListener(e -> openThemeSelector());
        
        // logout automático
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    ejecutarConsulta("LOGOUT " + token);
                } catch (Exception ex) {
                    System.out.println("Error enviando logout: " + ex.getMessage());
                }
            }
        });
    }

    // ------------------- Ejecutar consulta -------------------
    private void ejecutarConsulta(String consulta) {
        if (consulta == null || consulta.isEmpty()) {
            logError("No hay consulta para ejecutar.");
            return;
        }

        GestorSintaxis GS = new GestorSintaxis(this);
        try {
            if (GS.enviarConsulta(consulta)) {
                try {
                    ClienteConexion conexion = new ClienteConexion("localhost", 12345);
                    String respuestaServidor = conexion.enviarConsultaConToken(token, consulta);
                    logMessageWithoutEnter("Respuesta del servidor: ", Color.WHITE);
                    if (respuestaServidor.startsWith("RESULT ERROR")) {
                        logMessage(respuestaServidor, Color.RED);
                    } else {
                        logMessage(respuestaServidor, Color.GREEN);
                    }
                } catch (Exception ex) {
                    logError("Error al conectar con servidor: " + ex.getMessage());
                }
            } else {
                logError("ERROR de sintaxis: consulta no enviada.");
            }
        } catch (Exception e) {
            logError("ERROR inesperado: " + e.getMessage());
        }
    }

    // ------------------- Consola -------------------
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

    // ------------------- Wizard -------------------
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

    // ------------------- Guardar query -------------------
    private void guardarQueryActual() {
        JTextPane area = getCurrentTextPane();
        String contenido = area.getText().trim();

        if (contenido.isEmpty()) {
            JOptionPane.showMessageDialog(this, "El query está vacío, nada que guardar.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int index = tabs.getSelectedIndex() + 1; // QUERY 1, 2, 3
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

    // ------------------- Sacred Scroll con prompt -------------------
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

    // ------------------- Obtener JTextPane actual -------------------
    private JTextPane getCurrentTextPane() {
        JScrollPane scroll = (JScrollPane) tabs.getSelectedComponent();
        JViewport viewport = scroll.getViewport();
        return (JTextPane) viewport.getView();
    }

    // ------------------- Selector de tema -------------------
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

    // ------------------- Resaltar keywords optimizado -------------------
    private void resaltarKeywordsCompleto(JTextPane pane) {
        StyledDocument doc = pane.getStyledDocument();
        try {
            String text = doc.getText(0, doc.getLength());
            
            // Resetear todo el documento
            SimpleAttributeSet normal = new SimpleAttributeSet();
            StyleConstants.setForeground(normal, Color.BLACK);
            doc.setCharacterAttributes(0, doc.getLength(), normal, true);

            List<String> keywords = Arrays.asList("SUMMON", "DATABASE", "TABLE", "BURN", "BRING", "UTILIZE", "LOGOUT","TABLES","INT","VARCHAR","MANIFEST","DATABASES","DEPICT","SELF","STACKABLE");
            String upperText = text.toUpperCase();
            
            for (String kw : keywords) {
                int index = 0;
                while ((index = upperText.indexOf(kw, index)) >= 0) {
                    // Verificar que sea una palabra completa
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

    // ------------------- Listener simple para Document -------------------
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