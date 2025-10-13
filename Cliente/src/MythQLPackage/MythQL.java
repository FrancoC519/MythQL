package MythQLPackage;

import javax.swing.*;

public class MythQL {
    private static String host;
    private static int port;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Primero mostrar ventana de conexión
            MythQL_Connection connectionDialog = new MythQL_Connection(null);
            connectionDialog.setVisible(true);

            if (!connectionDialog.isConnected()) {
                System.out.println("Conexión cancelada por el usuario.");
                System.exit(0);
                return;
            }

            // Guardar configuración de conexión
            host = connectionDialog.getHost();
            port = connectionDialog.getPort();

            // Ahora mostrar login
            MythQL_Login login = new MythQL_Login(null, host, port);
            login.setVisible(true);

            String token = login.getToken();
            if (token != null) {
                MythQL_UI ui = new MythQL_UI(token, host, port);
                ui.setVisible(true);
            } else {
                System.out.println("No se pudo iniciar sesión.");
            }
        });
    }

    // Getters estáticos para acceso global
    public static String getHost() {
        return host;
    }

    public static int getPort() {
        return port;
    }
}