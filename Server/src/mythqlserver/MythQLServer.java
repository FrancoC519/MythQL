package mythqlserver;

import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MythQLServer {

    private static final int PORT = 12345;

    private UserStore userStore = new UserStore();
    private Map<String, User> sesiones = new ConcurrentHashMap<>();

    // UI opcional
    private MythQLServerUI serverUI;

    // Constructor sin UI (modo consola)
    public MythQLServer() {
        this.serverUI = null;
    }

    // Constructor con UI
    public MythQLServer(MythQLServerUI ui) {
        this.serverUI = ui;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            log("Servidor escuchando en puerto " + PORT, java.awt.Color.GREEN);

            while (true) {
                Socket socket = serverSocket.accept();
                log("Cliente conectado: " + socket.getInetAddress(), java.awt.Color.CYAN);

                ClientHandler handler;
                if (serverUI != null) {
                    // con UI: pasamos la función de log
                    handler = new ClientHandler(socket, userStore, sesiones, serverUI::logMessage);
                } else {
                    // sin UI: logs por consola
                    handler = new ClientHandler(socket, userStore, sesiones);
                }

                new Thread(handler).start();
            }
        } catch (Exception e) {
            log("Error en servidor: " + e.getMessage(), java.awt.Color.RED);
            e.printStackTrace();
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

    // Método main flexible: se puede lanzar con o sin UI
    public static void main(String[] args) {
        boolean usarUI = true; // ponelo en false si querés que arranque solo en consola

        if (usarUI) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                MythQLServerUI ui = new MythQLServerUI();
                ui.setVisible(true);

                MythQLServer server = new MythQLServer(ui);
                new Thread(server::start).start();
            });
        } else {
            new MythQLServer().start();
        }
    }
}
