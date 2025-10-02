package mythqlserver;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;

public class MythQLServerUI extends JFrame {

    private JTextPane consolePane;
    private StyledDocument doc;
    private JButton btnChangePort;

    public MythQLServerUI() {
        setTitle("MYTHQL Server Edition");
        setSize(800, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Panel superior
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topPanel.setBackground(new Color(108, 44, 120));
        JLabel title = new JLabel("MYTHQL Server Edition");
        title.setFont(new Font("Arial Black", Font.BOLD, 22));
        title.setForeground(Color.RED);
        topPanel.add(title);
        add(topPanel, BorderLayout.NORTH);

        // Consola central
        consolePane = new JTextPane();
        consolePane.setEditable(false);
        consolePane.setBackground(Color.BLACK);
        consolePane.setForeground(Color.WHITE);
        doc = consolePane.getStyledDocument();
        JScrollPane consoleScroll = new JScrollPane(consolePane);
        add(consoleScroll, BorderLayout.CENTER);

        // Panel inferior
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        add(bottomPanel, BorderLayout.SOUTH);
    }

    // Método para escribir en la consola
    public void logMessage(String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            try {
                Style style = consolePane.addStyle("Style", null);
                StyleConstants.setForeground(style, color);
                doc.insertString(doc.getLength(), message + "\n", style);
                consolePane.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    // Sobrecarga para mensajes verdes por defecto
    public void logMessage(String message) {
        logMessage(message, Color.GREEN);
    }

    public JButton getBtnChangePort() {
        return btnChangePort;
    }
}
