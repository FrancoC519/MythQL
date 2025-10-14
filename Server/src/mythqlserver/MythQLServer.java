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
    
    // Managers
    private NotificationManager notificationManager;
    private LoginManager loginManager;
    private QueryProcessor queryProcessor;

    public MythQLServer(int port) {
        this.port = port;
        this.serverUI = null;
        inicializarManagers();
    }

    public MythQLServer(MythQLServerUI ui, int port) {
        this.serverUI = ui;
        this.port = port;
        inicializarManagers();
    }

    private void inicializarManagers() {
        // Pasar el callback de logging a cada manager
        Consumer<String> logCallback = this::logToUI;
        
        this.notificationManager = new NotificationManager(logCallback);
        this.loginManager = new LoginManager(userStore, sesiones, logCallback);
        this.queryProcessor = new QueryProcessor(sesiones, notificationManager, logCallback);
        
        log("Managers inicializados: Login, Query, Notification", java.awt.Color.GREEN);
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            log("Servidor escuchando en puerto " + port, java.awt.Color.GREEN);

            while (true) {
                Socket socket = serverSocket.accept();
                log("Cliente conectado: " + socket.getInetAddress(), java.awt.Color.CYAN);

                SocketHandler handler = new SocketHandler(
                    socket, 
                    loginManager, 
                    queryProcessor, 
                    notificationManager,
                    sesiones
                );

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

    private void log(String msg, java.awt.Color color) {
        if (serverUI != null) {
            serverUI.logMessage(msg, color);
        } else {
            System.out.println(msg);
        }
    }
    private void logToUI(String msg) {
        log(msg, java.awt.Color.WHITE); // Color por defecto
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