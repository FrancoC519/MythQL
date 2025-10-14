package mythqlserver;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class NotificationManager {
    private Map<String, Socket> clientesSuscritos = new ConcurrentHashMap<>();
    private Consumer<String> logCallback;
    
    public NotificationManager(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }
    
    public void suscribirCliente(String token, Socket socket) {
        clientesSuscritos.put(token, socket);
        log("Cliente suscrito a notificaciones, token: " + token);
    }
    
    public void desuscribirCliente(String token) {
        Socket removed = clientesSuscritos.remove(token);
        if (removed != null) {
            log("Cliente desuscrito, token: " + token);
        }
    }
    
    public void broadcastNotificacion(String mensaje) {
        log("Broadcasting: " + mensaje);
        
        List<String> tokensARemover = new ArrayList<>();
        
        for (Map.Entry<String, Socket> entry : clientesSuscritos.entrySet()) {
            String token = entry.getKey();
            Socket socket = entry.getValue();
            
            try {
                if (socket.isClosed() || !socket.isConnected()) {
                    tokensARemover.add(token);
                    continue;
                }
                
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println("NOTIFICATION " + mensaje);
                log("Notificación enviada a token: " + token);
                
            } catch (IOException e) {
                log("Cliente desconectado, removiendo: " + token);
                tokensARemover.add(token);
            }
        }
        
        for (String token : tokensARemover) {
            clientesSuscritos.remove(token);
        }
    }
    
    private void log(String msg) {
        if (logCallback != null) {
            logCallback.accept(msg);
        } else {
            System.out.println(msg);
        }
    }
}