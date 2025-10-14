package mythqlserver;

import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class MythQLServer {
    private int port;
    private UserStore userStore = new UserStore();
    private Map<String, User> sesiones = new ConcurrentHashMap<>();
    private MythQLServerUI serverUI;
    private ServerSocket serverSocket;
    
    // Lista de consumidores para notificaciones
    private List<Consumer<String>> notificacionConsumers = new ArrayList<>();
    private Map<String, ClientHandler> clientesConectados = new ConcurrentHashMap<>();

    public MythQLServer(int port) {
        this.port = port;
        this.serverUI = null;
    }

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
                
                handler.setServer(this);
                String clientId = socket.getInetAddress() + ":" + socket.getPort();
                clientesConectados.put(clientId, handler);

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

    // Métodos para notificaciones
    public void suscribirANotificaciones(Consumer<String> consumer) {
        notificacionConsumers.add(consumer);
    }

    public void desuscribirDeNotificaciones(Consumer<String> consumer) {
        notificacionConsumers.remove(consumer);
    }

    public void broadcastNotificacion(String mensaje) {
        log("Broadcasting: " + mensaje, java.awt.Color.ORANGE);
        for (Consumer<String> consumer : new ArrayList<>(notificacionConsumers)) {
            try {
                consumer.accept(mensaje);
            } catch (Exception e) {
                desuscribirDeNotificaciones(consumer);
            }
        }
    }

    public void removerCliente(String clientId) {
        clientesConectados.remove(clientId);
    }

    private void log(String msg, java.awt.Color color) {
        if (serverUI != null) {
            serverUI.logMessage(msg, color);
        } else {
            System.out.println(msg);
        }
    }

    public static void main(String[] args) {
        boolean usarUI = true;

        if (usarUI) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                ServerConfigDialog configDialog = new ServerConfigDialog(null);
                configDialog.setVisible(true);

                if (!configDialog.isStarted()) {
                    System.out.println("Inicio del servidor cancelado por el usuario.");
                    System.exit(0);
                    return;
                }

                int port = configDialog.getPort();
                
                MythQLServerUI ui = new MythQLServerUI(port);
                ui.setVisible(true);

                MythQLServer server = new MythQLServer(ui, port);
                
                Thread serverThread = new Thread(server::start);
                serverThread.start();
                
                ui.setServer(server);
            });
        } else {
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