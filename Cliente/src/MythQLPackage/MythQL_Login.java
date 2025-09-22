package MythQLPackage;

import javax.swing.*;
import java.awt.*;

public class MythQL_Login extends JDialog {

    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private boolean authenticated = false;
    private User authenticatedUser;

    public MythQL_Login(Frame parent, UserStore userStore) {
        super(parent, "Login - MYTHQL", true); // modal
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

        // Acción login
        btnLogin.addActionListener(e -> {
            String username = txtUsername.getText().trim();
            String password = new String(txtPassword.getPassword());

            User user = userStore.authenticate(username, password);
            if (user != null) {
                authenticated = true;
                authenticatedUser = user;
                JOptionPane.showMessageDialog(this,
                        "Inicio de sesión exitoso.\nUsuario: " + user.getUsername() +
                        "\nRoles: " + user.getRoles(),
                        "Bienvenido", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this,
                        "Usuario o contraseña incorrectos.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Acción cancelar
        btnCancel.addActionListener(e -> {
            authenticated = false;
            dispose();
        });
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public User getAuthenticatedUser() {
        return authenticatedUser;
    }
}
