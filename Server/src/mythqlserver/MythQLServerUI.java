package mythqlserver;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;

public class MythQLServerUI extends JFrame {

    private JTextPane consolePane;
    private StyledDocument doc;
    private JButton btnStopServer;
    private JLabel lblPortInfo;
    private MythQLServer server;
    private int port;

    // Constructor modificado para recibir el puerto
    public MythQLServerUI(int port) {
        this.port = port;
        initializeUI();
    }

    private void initializeUI() {
        setTitle("MYTHQL Server Edition - Puerto: " + port);
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
        
        // Información del puerto
        lblPortInfo = new JLabel("Puerto: " + port);
        lblPortInfo.setFont(new Font("Arial", Font.BOLD, 14));
        lblPortInfo.setForeground(Color.WHITE);
        topPanel.add(Box.createHorizontalStrut(20));
        topPanel.add(lblPortInfo);
        
        // Botón para detener servidor
        btnStopServer = new JButton("Detener Servidor");
        btnStopServer.setBackground(new Color(255, 100, 100));
        btnStopServer.setForeground(Color.WHITE);
        btnStopServer.addActionListener(e -> detenerServidor());
        topPanel.add(Box.createHorizontalStrut(20));
        topPanel.add(btnStopServer);
        
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
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel lblStatus = new JLabel("Servidor ejecutándose en puerto: " + port);
        lblStatus.setForeground(new Color(0, 100, 0));
        bottomPanel.add(lblStatus);
        add(bottomPanel, BorderLayout.SOUTH);

        // Manejar cierre de ventana
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                detenerServidor();
            }
        });
    }

    private void detenerServidor() {
        if (server != null) {
            int confirm = JOptionPane.showConfirmDialog(this,
                "¿Está seguro de que desea detener el servidor?",
                "Confirmar Detención",
                JOptionPane.YES_NO_OPTION);
            
            if (confirm == JOptionPane.YES_OPTION) {
                server.stop();
                logMessage("Servidor detenido por el usuario", Color.YELLOW);
                btnStopServer.setEnabled(false);
            }
        }
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

    public void setServer(MythQLServer server) {
        this.server = server;
    }

    public void updatePort(int newPort) {
        this.port = newPort;
        lblPortInfo.setText("Puerto: " + port);
        setTitle("MYTHQL Server Edition - Puerto: " + port);
    }
}