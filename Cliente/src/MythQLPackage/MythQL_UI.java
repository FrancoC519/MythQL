package MythQLPackage;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;

public class MythQL_UI extends JFrame {
    private JTabbedPane tabs;
    private JTextPane consolePane;
    private JPanel topPanel, leftPanel, bottomPanel;
    private boolean mysqlTheme = false;
    private String token;

    public MythQL_UI(String token) {
        this.token = token;
        setTitle("MYTHQL");
        setSize(1100, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

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
        for (int i = 0; i < 4; i++) {
            JButton gear = new JButton("⚙");
            gear.setBackground(new Color(255, 204, 102));
            topPanel.add(gear);
        }
        add(topPanel, BorderLayout.NORTH);

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

        tabs = new JTabbedPane();
        for (int i = 1; i <= 3; i++) {
            JTextArea queryArea = new JTextArea();
            JScrollPane scroll = new JScrollPane(queryArea);
            tabs.add("QUERY " + i, scroll);
        }
        add(tabs, BorderLayout.CENTER);

        consolePane = new JTextPane();
        consolePane.setEditable(false);
        consolePane.setBackground(Color.BLACK);
        JScrollPane consoleScroll = new JScrollPane(consolePane);
        consoleScroll.setPreferredSize(new Dimension(0, 80));
        JButton btnGif = new JButton(new ImageIcon(getClass().getResource("/potato.gif")));
        btnGif.setBorderPainted(false);
        btnGif.setContentAreaFilled(false);
        btnGif.setFocusPainted(false);
        btnGif.setOpaque(false);
        btnGif.setPreferredSize(new Dimension(80, 80));
        btnGif.addActionListener(e -> {
            JFrame wizardFrame = new JFrame("Wizard");
            wizardFrame.setSize(300, 150);
            wizardFrame.setLocationRelativeTo(this);
            JLabel lblWizardText = new JLabel("Wizard Text", SwingConstants.CENTER);
            lblWizardText.setFont(new Font("Arial", Font.BOLD, 24));
            lblWizardText.setForeground(Color.RED);
            wizardFrame.add(lblWizardText);
            wizardFrame.setVisible(true);
        });
        bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(consoleScroll, BorderLayout.CENTER);
        bottomPanel.add(btnGif, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        btnExecute.addActionListener(e -> ejecutarConsulta(getCurrentTextArea().getText().trim()));
        btnExecuteSel.addActionListener(e -> {
            String sel = getCurrentTextArea().getSelectedText();
            if (sel == null || sel.isEmpty()) {
                try {
                    int caret = getCurrentTextArea().getCaretPosition();
                    int start = getCurrentTextArea().getLineStartOffset(getCurrentTextArea().getLineOfOffset(caret));
                    int end = getCurrentTextArea().getLineEndOffset(getCurrentTextArea().getLineOfOffset(caret));
                    sel = getCurrentTextArea().getText(start, end - start).trim();
                } catch (Exception ex) {
                    sel = "";
                }
            }
            ejecutarConsulta(sel);
        });
        btnSacred.addActionListener(e -> abrirSacredScroll());
        btnTheme.addActionListener(e -> toggleTheme());

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

    private void ejecutarConsulta(String consulta) {
        if (consulta == null || consulta.isEmpty()) {
            logMessage("ERROR: No hay consulta para ejecutar.", Color.RED);
            return;
        }
        if (consulta.equalsIgnoreCase("exit")) {
            logMessage("Saliendo del sistema...", Color.RED);
            System.exit(0);
            return;
        }
        GestorSintaxis GS = new GestorSintaxis();
        if (GS.enviarConsulta(consulta)) {
            logMessage("APROBADO.", Color.GREEN);
            try {
                ClienteConexion conexion = new ClienteConexion("localhost", 12345);
                String respuestaServidor = conexion.enviarConsultaConToken(token, consulta);
                logMessage("Respuesta del servidor: " + respuestaServidor, Color.GREEN);
            } catch (Exception ex) {
                logMessage("Error al conectar con servidor: " + ex.getMessage(), Color.RED);
            }
        } else {
            logMessage("ERROR de sintaxis: consulta no enviada.", Color.RED);
        }
    }

    private void toggleTheme() {
        if (!mysqlTheme) {
            topPanel.setBackground(new Color(230, 242, 255));
            leftPanel.setBackground(Color.WHITE);
            bottomPanel.setBackground(new Color(230, 242, 255));
            consolePane.setBackground(Color.WHITE);
            consolePane.setForeground(Color.BLACK);
        } else {
            topPanel.setBackground(new Color(108, 44, 120));
            leftPanel.setBackground(new Color(242, 242, 242));
            bottomPanel.setBackground(null);
            consolePane.setBackground(Color.BLACK);
            consolePane.setForeground(Color.WHITE);
        }
        mysqlTheme = !mysqlTheme;
        repaint();
    }

    private JTextArea getCurrentTextArea() {
        JScrollPane scroll = (JScrollPane) tabs.getSelectedComponent();
        JViewport viewport = scroll.getViewport();
        return (JTextArea) viewport.getView();
    }

    private void logMessage(String message, Color color) {
        StyledDocument doc = consolePane.getStyledDocument();
        Style style = consolePane.addStyle("Style", null);
        StyleConstants.setForeground(style, color);
        try {
            doc.insertString(doc.getLength(), message + "\n", style);
            consolePane.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void abrirSacredScroll() {
        JFrame sacredFrame = new JFrame("Sacred Scroll");
        sacredFrame.setSize(400, 400);
        sacredFrame.setLocationRelativeTo(this);
        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("text ");
            if (i % 20 == 0) sb.append("\n");
        }
        textArea.setText(sb.toString());
        sacredFrame.add(new JScrollPane(textArea));
        sacredFrame.setVisible(true);
    }
}