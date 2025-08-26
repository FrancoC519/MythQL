package MythQLPackage;

import java.util.Scanner;

public class MythQL {
    public static void main(String[] args) {
        UserStore userStore = new UserStore();
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Sistema de Inicio de Sesión ===");
        while (true) {
            System.out.print("\nIngrese su nombre de usuario (0 para salir): ");
            String username = scanner.nextLine();

            if (username.equals("0")) {
                System.out.println("Saliendo del sistema...");
                break;
            }

            System.out.print("Ingrese su contraseña: ");
            String password = scanner.nextLine();

            User user = userStore.authenticate(username, password);

            if (user != null) {
                System.out.println("Inicio de sesión exitoso.");
                System.out.println("Usuario: " + user.getUsername());
                System.out.println("Roles: " + user.getRoles());
                break;
            } else {
                System.out.println("Usuario o contraseña incorrectos. Intente nuevamente.");
            }
        }

        scanner.close();
    }
}
