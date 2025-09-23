package MythQLPackage;

import javax.swing.*;

public class MythQL {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UserStore userStore = new UserStore();

            MythQL_Login loginDialog = new MythQL_Login(null, userStore);
            loginDialog.setVisible(true);

            if (loginDialog.isAuthenticated()) {
                new MythQL_UI().setVisible(true);
            } else {
                System.out.println("Aplicación cerrada: login fallido o cancelado.");
                System.exit(0);
            }
        });
    }
}