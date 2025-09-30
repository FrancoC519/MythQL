package MythQLPackage;

import javax.swing.*;
import java.awt.*;

public class MythQL_Login extends JDialog {
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private String token = null;

    public MythQL_Login(Frame parent) {
        super(parent, "Login - MYTHQL", true);
        setSize(300, 200);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        JPanel panel = new JPanel(new GridLayout(3, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

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

            ClienteConexion conn = new ClienteConexion("localhost", 12345);
            token = conn.login(username, password); // ✅ método login ahora existe

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
