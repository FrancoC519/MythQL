package MythQLPackage;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class ClienteConexion {
    private String host;
    private int port;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean escuchando = false;
    private Thread hiloEscucha;

    public ClienteConexion(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public boolean testConexion() {
        try (Socket socket = new Socket(host, port)) {
            return socket.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    public String login(String username, String password) {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("LOGIN " + username + " " + password);
            String respuesta = in.readLine();
            if (respuesta != null && respuesta.startsWith("OK")) {
                return respuesta.split(" ")[1];
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String enviarConsultaConToken(String token, String consulta) {
        try {
            if (socket == null || socket.isClosed()) {
                socket = new Socket(host, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            }
            
            if (consulta.startsWith("LOGOUT")){
                out.println(consulta);
                String respuesta = in.readLine();
                detenerEscucha();
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                return respuesta;
            } else {
                out.println("QUERY " + token + " " + consulta);
                return in.readLine();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: no se pudo conectar al servidor.";
        }
    }

    // NUEVO: Obtener esquemas jerárquicos
    public String obtenerEsquemas(String token) {
        try {
            if (socket == null || socket.isClosed()) {
                socket = new Socket(host, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            }
            
            out.println("GET_SCHEMAS " + token);
            String respuesta = in.readLine();
            if (respuesta != null && respuesta.startsWith("SCHEMAS ")) {
                return respuesta.substring("SCHEMAS ".length());
            }
            return "ERROR: " + respuesta;
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    public void iniciarEscuchaNotificaciones(Consumer<String> manejadorNotificacion) {
        if (escuchando) return;
        
        escuchando = true;
        hiloEscucha = new Thread(() -> {
            try {
                String linea;
                while (escuchando && (linea = in.readLine()) != null) {
                    if (linea.startsWith("NOTIFICATION ")) {
                        String notificacion = linea.substring("NOTIFICATION ".length());
                        manejadorNotificacion.accept(notificacion);
                    }
                }
            } catch (IOException e) {
                if (escuchando) {
                    System.err.println("Error en escucha de notificaciones: " + e.getMessage());
                }
            }
        });
        hiloEscucha.start();
    }

    public void detenerEscucha() {
        escuchando = false;
        if (hiloEscucha != null) {
            hiloEscucha.interrupt();
        }
    }

    public String suscribirANotificaciones(String token) {
        try {
            // Asegurarse de que hay conexión
            if (socket == null || socket.isClosed()) {
                socket = new Socket(host, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            }

            // Ahora sí out no debería ser null
            out.println("SUBSCRIBE " + token);
            return in.readLine();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}