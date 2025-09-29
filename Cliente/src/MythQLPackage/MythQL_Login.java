package MythQLPackage;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class MythQL_Login extends JDialog {
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private boolean authenticated = false;
    private String authenticatedUser; // ahora solo guardamos el nombre del usuario

    public MythQL_Login(Frame parent) {
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

            if (enviarLoginAlServidor(username, password)) {
                authenticated = true;
                authenticatedUser = username;
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

        // Acción cancelar
        btnCancel.addActionListener(e -> {
            authenticated = false;
            dispose();
        });
    }

    private boolean enviarLoginAlServidor(String username, String password) {
        try (Socket socket = new Socket("localhost", 12345);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Enviar comando LOGIN al servidor
            out.println(username + "\n" + password);

            // Esperar respuesta
            String respuesta = in.readLine();
            System.out.println(respuesta);
            return respuesta != null && respuesta.startsWith("OK");

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getAuthenticatedUser() {
        return authenticatedUser;
    }
}