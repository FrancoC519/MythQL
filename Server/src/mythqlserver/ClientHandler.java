package mythqlserver;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private UserStore userStore;
    private Map<String, User> sesiones;

    public ClientHandler(Socket socket, UserStore userStore, Map<String, User> sesiones) {
        this.socket = socket;
        this.userStore = userStore;
        this.sesiones = sesiones;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String mensaje;
            while ((mensaje = in.readLine()) != null) {
                System.out.println("Recibido: " + mensaje);

                String[] partes = mensaje.split(" ", 3);
                if (partes.length < 1) {
                    out.println("ERROR formato inválido");
                    continue;
                }

                String comando = partes[0].toUpperCase();
                switch (comando) {
                    case "LOGIN":
                        if (partes.length < 3) {
                            out.println("ERROR Uso: LOGIN usuario contraseña");
                            break;
                        }
                        manejarLogin(partes[1], partes[2], out);
                        break;

                    case "QUERY":
                        if (partes.length < 3) {
                            out.println("ERROR Uso: QUERY token consulta");
                            break;
                        }
                        manejarConsulta(partes[1], partes[2], out);
                        break;

                    default:
                        out.println("ERROR Comando desconocido");
                }
            }

        } catch (Exception e) {
            System.err.println("Error en cliente: " + e.getMessage());
        }
    }

    private void manejarLogin(String username, String password, PrintWriter out) {
        User user = userStore.authenticate(username, password);
        if (user != null) {
            String token = UUID.randomUUID().toString();
            sesiones.put(token, user);
            out.println("OK " + token);
            System.out.println("Usuario autenticado: " + username + " -> Token: " + token);
        } else {
            out.println("ERROR credenciales inválidas");
        }
    }

    private void manejarConsulta(String token, String consulta, PrintWriter out) {
        User user = sesiones.get(token);
        if (user == null) {
            out.println("ERROR sesión no válida");
            return;
        }

        out.println("RESULT Consulta ejecutada por " + user.getUsername() + ": " + consulta);
        System.out.println("Consulta de " + user.getUsername() + ": " + consulta);
    }
}
