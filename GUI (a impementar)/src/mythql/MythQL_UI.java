package mythql;

import java.awt.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class MythQL_UI extends JFrame {

    private JTabbedPane tabs;  // lo hacemos global para acceder en listeners

    public MythQL_UI() {
        setTitle("MYTHQL");
        setSize(1100, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());

        // ----- HEADER SUPERIOR -----
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topPanel.setBackground(new Color(108, 44, 120)); // violeta
        JLabel title = new JLabel("MYTHQL");
        title.setFont(new Font("Arial Black", Font.BOLD, 22));
        title.setForeground(Color.RED);
        topPanel.add(title);

        JButton btnExecute = new JButton("Execute");
        JButton btnExecuteSel = new JButton("Execute Selected");
        JButton btnSacred = new JButton("Sacred Scroll"); // nuevo botón

        topPanel.add(btnExecute);
        topPanel.add(btnExecuteSel);
        topPanel.add(btnSacred);

        for (int i = 0; i < 4; i++) {
            JButton gear = new JButton("⚙");
            gear.setBackground(new Color(255, 204, 102));
            topPanel.add(gear);
        }

        add(topPanel, BorderLayout.NORTH);

        // ----- PANEL IZQUIERDO -----
        JPanel leftPanel = new JPanel();
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

        // ----- PESTAÑAS CENTRAL -----
        tabs = new JTabbedPane();
        for (int i = 1; i <= 3; i++) {
            JTextArea queryArea = new JTextArea();
            JScrollPane scroll = new JScrollPane(queryArea);
            tabs.add("QUERY " + i, scroll);
        }
        add(tabs, BorderLayout.CENTER);

        // ----- CONSOLA INFERIOR -----
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(Color.BLACK);
        bottom.setPreferredSize(new Dimension(0, 60));

        // Texto a la izquierda
        JLabel console = new JLabel("<html>"
                + "<font color='red'>* Errors come in Red, ask the wizard about error, click the icon on the side<br>"
                + "</font><font color='green'>* Otherwise, Green</font></html>");
        console.setFont(new Font("Arial", Font.BOLD, 12));
        console.setForeground(Color.WHITE); // para que contraste mejor con negro
        bottom.add(console, BorderLayout.WEST);

        // Cargar GIF original
        ImageIcon iconGif = new ImageIcon("/home/usrlocal/NetBeansProjects/MythQL/C0ej07b.gif");

        // Escalar el GIF (manteniendo animación)
        int newW = 80;  // ancho deseado
        int newH = 80;  // alto deseado
        Image img = iconGif.getImage().getScaledInstance(newW, newH, Image.SCALE_DEFAULT);
        ImageIcon scaledIcon = new ImageIcon(img);

        // Botón con el GIF escalado
        JButton btnGif = new JButton(scaledIcon);
        btnGif.setBorderPainted(false);
        btnGif.setContentAreaFilled(false);
        btnGif.setFocusPainted(false);
        btnGif.setOpaque(false);
        btnGif.setPreferredSize(new Dimension(newW, newH));

        // Acción al clickear el GIF
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

        bottom.add(btnGif, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);


        // ----- EVENTOS BOTONES -----
        btnExecute.addActionListener(e -> ejecutarTodo());
        btnExecuteSel.addActionListener(e -> ejecutarSeleccionado());
        btnSacred.addActionListener(e -> abrirSacredScroll()); // evento del nuevo botón
    }

    // Obtener el JTextArea activo
    private JTextArea getCurrentTextArea() {
        JScrollPane scroll = (JScrollPane) tabs.getSelectedComponent();
        JViewport viewport = scroll.getViewport();
        return (JTextArea) viewport.getView();
    }

    private void ejecutarTodo() {
        JTextArea area = getCurrentTextArea();
        String texto = area.getText().trim();
        if (!texto.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ejecutado");
        } else {
            JOptionPane.showMessageDialog(this, "No hay código para ejecutar");
        }
    }

    private void ejecutarSeleccionado() {
        JTextArea area = getCurrentTextArea();
        String seleccionado = area.getSelectedText();

        if (seleccionado == null || seleccionado.isEmpty()) {
            // si no hay selección, tomar la línea actual
            try {
                int caret = area.getCaretPosition();
                int start = area.getLineStartOffset(area.getLineOfOffset(caret));
                int end = area.getLineEndOffset(area.getLineOfOffset(caret));
                seleccionado = area.getText(start, end - start).trim();
            } catch (Exception ex) {
                seleccionado = "";
            }
        }

        if (!seleccionado.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Selección ejecutada");
        } else {
            JOptionPane.showMessageDialog(this, "No hay selección para ejecutar");
        }
    }

    private void abrirSacredScroll() {
        JFrame sacredFrame = new JFrame("Sacred Scroll");
        sacredFrame.setSize(400, 400);
        sacredFrame.setLocationRelativeTo(this);

        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);

        // Repetimos la palabra "text" muchas veces
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("text ");
            if (i % 20 == 0) sb.append("\n"); // salto de línea cada 20
        }
        textArea.setText(sb.toString());

        sacredFrame.add(new JScrollPane(textArea));
        sacredFrame.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new MythQL_UI().setVisible(true);
        });
    }
}