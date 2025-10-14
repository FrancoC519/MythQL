package mythqlserver;

import java.io.*;
import java.net.Socket;
import java.util.Map;

public class SocketHandler implements Runnable {
    private Socket socket;
    private LoginManager loginManager;
    private QueryProcessor queryProcessor;
    private NotificationManager notificationManager;
    private Map<String, User> sesiones;
    
    public SocketHandler(Socket socket, LoginManager loginManager, 
                        QueryProcessor queryProcessor, NotificationManager notificationManager,
                        Map<String, User> sesiones) {
        this.socket = socket;
        this.loginManager = loginManager;
        this.queryProcessor = queryProcessor;
        this.notificationManager = notificationManager;
        this.sesiones = sesiones;
    }
    
    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            
            String mensaje;
            while ((mensaje = in.readLine()) != null) {
                System.out.println("Mensaje recibido: " + mensaje);
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
                        loginManager.procesarLogin(partes[1], partes[2], out);
                        break;
                        
                    case "QUERY":
                        if (partes.length < 3) {
                            out.println("ERROR Uso: QUERY token consulta");
                            break;
                        }
                        queryProcessor.procesarQuery(partes[1], partes[2], out);
                        break;
                        
                    case "SUBSCRIBE":
                        if (partes.length < 2) {
                            out.println("ERROR Uso: SUBSCRIBE token");
                            break;
                        }
                        notificationManager.suscribirCliente(partes[1], socket);
                        out.println("OK Suscrito a notificaciones");
                        break;
                        
                    case "LOGOUT":
                        if (partes.length < 2) {
                            out.println("ERROR Uso: LOGOUT token");
                            break;
                        }
                        notificationManager.desuscribirCliente(partes[1]);
                        // También remover de sesiones
                        User removedUser = sesiones.remove(partes[1]);
                        if (removedUser != null) {
                            System.out.println("Usuario deslogueado: " + removedUser.getUsername());
                        }
                        out.println("OK Logout correcto");
                        break;
                        
                    case "GET_SCHEMAS":
                        if (partes.length < 2) {
                            out.println("ERROR Uso: GET_SCHEMAS token");
                            break;
                        }
                        manejarGetSchemas(partes[1], out);
                        break;
                        
                    default:
                        out.println("ERROR Comando desconocido: " + comando);
                }
            }
        } catch (IOException e) {
            System.out.println("Cliente desconectado: " + socket.getInetAddress() + " - " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Error cerrando socket: " + e.getMessage());
            }
        }
    }
    
    private void manejarGetSchemas(String token, PrintWriter out) {
        User user = sesiones.get(token);
        if (user == null) {
            out.println("ERROR token invalido");
            return;
        }

        try {
            String esquemas = queryProcessor.obtenerEsquemasJerarquicos();
            out.println("SCHEMAS " + esquemas);
        } catch (Exception e) {
            out.println("ERROR " + e.getMessage());
        }
    }
}