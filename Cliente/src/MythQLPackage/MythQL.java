package MythQLPackage;

import javax.swing.*;

public class MythQL {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MythQL_Login login = new MythQL_Login(null);
            login.setVisible(true);

            String token = login.getToken();
            if (token != null) {
                MythQL_UI ui = new MythQL_UI(token); // ✅ token recibido
                ui.setVisible(true);
            } else {
                System.out.println("No se pudo iniciar sesión.");
            }
        });
    }
}
