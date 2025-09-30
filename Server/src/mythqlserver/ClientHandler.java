package mythqlserver;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class ClientHandler implements Runnable {
    private static final long TIMEOUT = 600000;
    private Socket socket;
    private UserStore userStore;
    private Map<String, User> sesiones;
    private String tokenAsignado = null;
    private long lastActivity;

    public ClientHandler(Socket socket, UserStore userStore, Map<String, User> sesiones) {
        this.socket = socket;
        this.userStore = userStore;
        this.sesiones = sesiones;
        this.lastActivity = System.currentTimeMillis();
    }

    @Override
    public void run() {
        new Thread(this::monitorTimeout).start();
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            String mensaje;
            while ((mensaje = in.readLine()) != null) {
                lastActivity = System.currentTimeMillis();
                System.out.println("Recibido: " + mensaje);
                String[] partes = mensaje.split(" ", 3);
                if (partes.length < 1) {
                    out.println("ERROR formato invalido");
                    continue;
                }
                String comando = partes[0].toUpperCase();
                switch (comando) {
                    case "LOGIN":
                        if (partes.length < 3) {
                            out.println("ERROR Uso: LOGIN usuario contrasena");
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
                    case "LOGOUT":
                        if (partes.length < 2) {
                            out.println("ERROR Uso: LOGOUT token");
                            break;
                        }
                        manejarLogout(partes[1], out);
                        return;
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
        if (user == null) {
            out.println("ERROR credenciales invalidas");
            return;
        }
        synchronized (sesiones) {
            boolean yaLogueado = sesiones.values().stream()
                    .anyMatch(u -> u.getUsername().equals(username));
            if (yaLogueado) {
                out.println("ERROR usuario ya logueado en otra sesion");
                return;
            }
            String token = UUID.randomUUID().toString();
            sesiones.put(token, user);
            tokenAsignado = token;
            out.println("OK " + token);
            System.out.println("Usuario autenticado: " + username + " -> Token: " + token);
        }
    }

    private void manejarConsulta(String token, String consulta, PrintWriter out) {
        User user = sesiones.get(token);
        if (user == null) {
            out.println("ERROR sesion no valida");
            return;
        }
        GestorConsultas GC = new GestorConsultas();
        String resultado = GC.procesarConsulta(consulta);
        out.println("RESULT " + resultado);
        System.out.println("Consulta de " + user.getUsername() + ": " + consulta);
    }

    private void manejarLogout(String token, PrintWriter out) {
        User removed = sesiones.remove(token);
        if (removed != null) {
            out.println("OK Logout correcto");
            System.out.println("Usuario " + removed.getUsername() + " deslogueado (token " + token + ")");
        } else {
            out.println("ERROR token no valido");
        }
    }

    private void monitorTimeout() {
        try {
            while (true) {
                Thread.sleep(30000);
                if (tokenAsignado != null &&
                        System.currentTimeMillis() - lastActivity > TIMEOUT) {
                    sesiones.remove(tokenAsignado);
                    System.out.println("Sesion expirada por inactividad: " + tokenAsignado);
                    socket.close();
                    break;
                }
            }
        } catch (Exception ignored) {}
    }
}