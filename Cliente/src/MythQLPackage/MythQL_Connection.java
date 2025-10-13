package MythQLPackage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class MythQL_Connection extends JDialog {
    private JTextField txtIP;
    private JTextField txtPort;
    private JButton btnConnect;
    private JButton btnCancel;
    private boolean connected = false;
    private String host;
    private int port;

    public MythQL_Connection(Frame parent) {
        super(parent, "Configuración de Conexión - MYTHQL", true);
        setSize(350, 200);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        setResizable(false);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Etiqueta y campo para IP
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Servidor (IP):"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        txtIP = new JTextField("localhost");
        panel.add(txtIP, gbc);

        // Etiqueta y campo para Puerto
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        panel.add(new JLabel("Puerto:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        txtPort = new JTextField("12345");
        panel.add(txtPort, gbc);

        // Panel de botones
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 0.0;
        JPanel buttonPanel = new JPanel(new FlowLayout());
        
        btnConnect = new JButton("Conectar");
        btnCancel = new JButton("Cancelar");
        
        buttonPanel.add(btnConnect);
        buttonPanel.add(btnCancel);
        
        panel.add(buttonPanel, gbc);

        add(panel, BorderLayout.CENTER);

        // Listeners
        btnConnect.addActionListener(e -> conectarServidor());
        btnCancel.addActionListener(e -> {
            connected = false;
            dispose();
        });

        // Enter en los campos de texto también ejecuta conexión
        ActionListener connectAction = e -> conectarServidor();
        txtIP.addActionListener(connectAction);
        txtPort.addActionListener(connectAction);

        // Cerrar con ESC
        panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        panel.getActionMap().put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                connected = false;
                dispose();
            }
        });
    }

    private void conectarServidor() {
        String ip = txtIP.getText().trim();
        String portStr = txtPort.getText().trim();

        if (ip.isEmpty() || portStr.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Por favor, complete ambos campos.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            int port = Integer.parseInt(portStr);
            
            // Test de conexión
            if (testConexion(ip, port)) {
                this.host = ip;
                this.port = port;
                this.connected = true;
                JOptionPane.showMessageDialog(this,
                    "Conexión exitosa con el servidor.",
                    "Éxito", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this,
                    "No se pudo conectar al servidor.\nVerifique la IP y puerto.",
                    "Error de Conexión", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                "El puerto debe ser un número válido.",
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean testConexion(String host, int port) {
        try {
            ClienteConexion conexion = new ClienteConexion(host, port);
            // Intentamos una conexión simple (podrías implementar un comando PING en el servidor)
            return conexion.testConexion();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}