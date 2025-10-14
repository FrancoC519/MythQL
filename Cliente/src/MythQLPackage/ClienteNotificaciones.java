package MythQLPackage;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class ClienteNotificaciones {
    private String host;
    private int port;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean escuchando = false;
    private Thread hiloEscucha;
    private Consumer<String> manejadorNotificacion;

    public ClienteNotificaciones(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // Conectar y suscribirse a notificaciones
    public boolean conectarYSuscribir(String token, Consumer<String> manejador) {
        try {
            this.manejadorNotificacion = manejador;
            
            // Crear conexión DEDICADA para notificaciones
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Suscribirse
            out.println("SUBSCRIBE " + token);
            String respuesta = in.readLine();
            
            if (respuesta != null && respuesta.startsWith("OK")) {
                iniciarEscucha();
                return true;
            } else {
                System.err.println("Error suscribiéndose: " + respuesta);
                cerrarConexion();
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("Error conectando para notificaciones: " + e.getMessage());
            cerrarConexion();
            return false;
        }
    }

    // Iniciar el hilo en segundo plano
    private void iniciarEscucha() {
        if (escuchando) return;
        
        escuchando = true;
        hiloEscucha = new Thread(() -> {
            System.out.println("Hilo de notificaciones iniciado...");
            
            try {
                String linea;
                // ↓ ↓ ↓ ESTE ES EL LOOP INFINITO EN SEGUNDO PLANO ↓ ↓ ↓
                while (escuchando && (linea = in.readLine()) != null) {
                    System.out.println("Notificación recibida: " + linea);
                    
                    if (linea.startsWith("NOTIFICATION ")) {
                        String notificacion = linea.substring("NOTIFICATION ".length());
                        if (manejadorNotificacion != null) {
                            manejadorNotificacion.accept(notificacion);
                        }
                    }
                }
            } catch (IOException e) {
                if (escuchando) {
                    System.err.println("Error en escucha de notificaciones: " + e.getMessage());
                }
            } finally {
                System.out.println("Hilo de notificaciones terminado.");
            }
        });
        
        hiloEscucha.setDaemon(true); // Para que no impida que la app se cierre
        hiloEscucha.start();
    }

    // Detener la escucha
    public void detener() {
        escuchando = false;
        if (hiloEscucha != null) {
            hiloEscucha.interrupt();
        }
        cerrarConexion();
    }

    private void cerrarConexion() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error cerrando conexión de notificaciones: " + e.getMessage());
        }
    }

    public boolean estaConectado() {
        return socket != null && !socket.isClosed() && escuchando;
    }
}