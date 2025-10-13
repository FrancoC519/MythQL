package mythqlserver;

import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MythQLServer {

    private int port; // Ya no es final
    private UserStore userStore = new UserStore();
    private Map<String, User> sesiones = new ConcurrentHashMap<>();
    private MythQLServerUI serverUI;
    private ServerSocket serverSocket;

    // Constructor sin UI (modo consola) - ahora recibe puerto
    public MythQLServer(int port) {
        this.port = port;
        this.serverUI = null;
    }

    // Constructor con UI - ahora recibe puerto
    public MythQLServer(MythQLServerUI ui, int port) {
        this.serverUI = ui;
        this.port = port;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            log("Servidor escuchando en puerto " + port, java.awt.Color.GREEN);

            while (true) {
                Socket socket = serverSocket.accept();
                log("Cliente conectado: " + socket.getInetAddress(), java.awt.Color.CYAN);

                ClientHandler handler;
                if (serverUI != null) {
                    handler = new ClientHandler(socket, userStore, sesiones, serverUI::logMessage);
                } else {
                    handler = new ClientHandler(socket, userStore, sesiones);
                }

                new Thread(handler).start();
            }
        } catch (Exception e) {
            if (!e.getMessage().contains("socket closed")) {
                log("Error en servidor: " + e.getMessage(), java.awt.Color.RED);
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                log("Servidor detenido", java.awt.Color.YELLOW);
            }
        } catch (Exception e) {
            log("Error al detener servidor: " + e.getMessage(), java.awt.Color.RED);
        }
    }

    // Método de logging unificado
    private void log(String msg, java.awt.Color color) {
        if (serverUI != null) {
            serverUI.logMessage(msg, color);
        } else {
            System.out.println(msg);
        }
    }

    // Método main flexible modificado
    public static void main(String[] args) {
        boolean usarUI = true;

        if (usarUI) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                // Mostrar diálogo de configuración primero
                ServerConfigDialog configDialog = new ServerConfigDialog(null);
                configDialog.setVisible(true);

                if (!configDialog.isStarted()) {
                    System.out.println("Inicio del servidor cancelado por el usuario.");
                    System.exit(0);
                    return;
                }

                int port = configDialog.getPort();
                
                // Ahora crear la UI principal del servidor
                MythQLServerUI ui = new MythQLServerUI(port);
                ui.setVisible(true);

                // Pasar el puerto seleccionado al servidor
                MythQLServer server = new MythQLServer(ui, port);
                
                // Iniciar servidor en hilo separado
                Thread serverThread = new Thread(server::start);
                serverThread.start();
                
                // Configurar el servidor en la UI para poder detenerlo
                ui.setServer(server);
            });
        } else {
            // Modo consola - usar puerto por defecto o argumento
            int port = 12345;
            if (args.length > 0) {
                try {
                    port = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    System.out.println("Puerto inválido. Usando puerto por defecto: 12345");
                }
            }
            System.out.println("Iniciando servidor en modo consola, puerto: " + port);
            new MythQLServer(port).start();
        }
    }
}