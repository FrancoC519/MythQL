package mythqlserver;

import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MythQLServer {
    private static final int PORT = 12345;

    private UserStore userStore = new UserStore();

    private Map<String, User> sesiones = new ConcurrentHashMap<>();

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor escuchando en puerto " + PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Cliente conectado: " + socket.getInetAddress());
                ClientHandler handler = new ClientHandler(socket, userStore, sesiones);
                new Thread(handler).start();
            }
        } catch (Exception e) {
            System.err.println("Error en servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new MythQLServer().start();
    }
}