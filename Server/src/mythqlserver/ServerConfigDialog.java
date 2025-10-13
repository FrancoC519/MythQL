package mythqlserver;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.ServerSocket;

public class ServerConfigDialog extends JDialog {
    private JTextField txtPort;
    private JButton btnStart;
    private JButton btnCancel;
    private int port;
    private boolean started = false;

    public ServerConfigDialog(Frame parent) {
        super(parent, "Configuración del Servidor - MYTHQL", true);
        setSize(350, 200);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        setResizable(false);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Título
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JLabel lblTitle = new JLabel("Configurar Puerto del Servidor", JLabel.CENTER);
        lblTitle.setFont(new Font("Arial", Font.BOLD, 14));
        panel.add(lblTitle, gbc);

        // Puerto
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        panel.add(new JLabel("Puerto:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        txtPort = new JTextField("12345");
        panel.add(txtPort, gbc);

        // Panel de botones
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 0.0;
        JPanel buttonPanel = new JPanel(new FlowLayout());
        
        btnStart = new JButton("Iniciar Servidor");
        btnCancel = new JButton("Cancelar");
        
        buttonPanel.add(btnStart);
        buttonPanel.add(btnCancel);
        
        panel.add(buttonPanel, gbc);

        add(panel, BorderLayout.CENTER);

        // Listeners
        btnStart.addActionListener(e -> iniciarServidor());
        btnCancel.addActionListener(e -> {
            started = false;
            dispose();
        });

        // Enter en el campo de puerto también inicia servidor
        txtPort.addActionListener(e -> iniciarServidor());

        // Cerrar con ESC
        panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        panel.getActionMap().put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                started = false;
                dispose();
            }
        });
    }

    private void iniciarServidor() {
        String portStr = txtPort.getText().trim();

        if (portStr.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Por favor, ingrese un número de puerto.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            int port = Integer.parseInt(portStr);
            
            if (port < 1 || port > 65535) {
                JOptionPane.showMessageDialog(this,
                    "El puerto debe estar entre 1 y 65535.",
                    "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Verificar si el puerto está disponible
            if (!puertoDisponible(port)) {
                JOptionPane.showMessageDialog(this,
                    "El puerto " + port + " no está disponible.\n" +
                    "Puede estar en uso por otra aplicación.",
                    "Puerto Ocupado", JOptionPane.ERROR_MESSAGE);
                return;
            }

            this.port = port;
            this.started = true;
            dispose();
            
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                "El puerto debe ser un número válido.",
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean puertoDisponible(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isStarted() {
        return started;
    }

    public int getPort() {
        return port;
    }
}