package MythQLPackage;

import javax.swing.*;
import java.awt.*;

public class MythQL_Login extends JDialog {
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private String token = null;
    private String host;
    private int port;

    // Constructor modificado para aceptar host y port
    public MythQL_Login(Frame parent, String host, int port) {
        super(parent, "Login - MYTHQL", true);
        this.host = host;
        this.port = port;
        
        setSize(300, 200);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10)); // Una fila más para info de conexión
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Información de conexión (solo lectura)
        panel.add(new JLabel("Servidor:"));
        JTextField txtServerInfo = new JTextField(host + ":" + port);
        txtServerInfo.setEditable(false);
        txtServerInfo.setBackground(new Color(240, 240, 240));
        panel.add(txtServerInfo);

        panel.add(new JLabel("Usuario:"));
        txtUsername = new JTextField();
        panel.add(txtUsername);

        panel.add(new JLabel("Contraseña:"));
        txtPassword = new JPasswordField();
        panel.add(txtPassword);

        JButton btnLogin = new JButton("Iniciar Sesión");
        JButton btnCancel = new JButton("Cancelar");
        panel.add(btnLogin);
        panel.add(btnCancel);

        add(panel, BorderLayout.CENTER);

        btnLogin.addActionListener(e -> {
            String username = txtUsername.getText().trim();
            String password = new String(txtPassword.getPassword());

            // Usar el host y port configurados
            ClienteConexion conn = new ClienteConexion(host, port);
            token = conn.login(username, password);
            if (token != null) {
                JOptionPane.showMessageDialog(this,
                        "Inicio de sesión exitoso.\nUsuario: " + username,
                        "Bienvenido", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this,
                        "Usuario o contraseña incorrectos.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnCancel.addActionListener(e -> {
            token = null;
            dispose();
        });
    }

    public String getToken() {
        return token;
    }
}