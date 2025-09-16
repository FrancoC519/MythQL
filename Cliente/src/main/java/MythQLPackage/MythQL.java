package MythQLPackage;

import java.util.Scanner;

public class MythQL {
    public static void main(String[] args) {
        UserStore userStore = new UserStore();
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Sistema de Inicio de Sesion ===");
        while (true) {
            System.out.print("\nIngrese su nombre de usuario (0 para salir): ");
            String username = scanner.nextLine();

            if (username.equals("0")) {
                System.out.println("Saliendo del sistema...");
                break;
            }

            System.out.print("Ingrese su contrasena: ");
            String password = scanner.nextLine();

            User user = userStore.authenticate(username, password);

            if (user != null) {
                System.out.println("Inicio de sesion exitoso.");
                System.out.println("Usuario: " + user.getUsername());
                System.out.println("Roles: " + user.getRoles());
                while(true){
                    System.out.print("Ingrese una consulta o exit para terminar la sesion: ");
                    String consulta  = scanner.nextLine();
                    if (consulta.equals("exit")){
                        System.out.print("Saliendo del sistema...");
                        break;
                    }else{
                        GestorSintaxis GS = new GestorSintaxis();
<<<<<<< HEAD
                        Boolean respuesta = GS.enviarConsulta(consulta);
                        if (respuesta == true){
                            System.out.println("APROBADO.");
                        }
=======
                        if (GS.enviarConsulta(consulta)) {
                        ClienteConexion conexion = new ClienteConexion("localhost", 12345);
                        String respuestaServidor = conexion.enviarConsulta(consulta);
                    System.out.println("Respuesta del servidor: " + respuestaServidor);
                    } else {
                    System.out.println("ERROR de sintaxis: consulta no enviada.");
                    }
>>>>>>> 3290dd47d9d26a0d96c538c9229763dffbae8abf
                    }
                }
            } else {
                System.out.println("Usuario o contrasena incorrectos. Intente nuevamente.");
            }
        }

        scanner.close();
    }
}
