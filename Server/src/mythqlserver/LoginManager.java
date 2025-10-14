package mythqlserver;

import java.io.PrintWriter;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class LoginManager {
    private UserStore userStore;
    private Map<String, User> sesiones;
    private Consumer<String> logCallback;
    
    public LoginManager(UserStore userStore, Map<String, User> sesiones, Consumer<String> logCallback) {
        this.userStore = userStore;
        this.sesiones = sesiones;
        this.logCallback = logCallback;
    }
    
    public void procesarLogin(String username, String password, PrintWriter out) {
        User user = userStore.authenticate(username, password);
        if (user == null) {
            out.println("ERROR credenciales invalidas");
            log("Intento de login fallido para usuario: " + username);
            return;
        }

        synchronized (sesiones) {
            boolean yaLogueado = sesiones.values().stream()
                    .anyMatch(u -> u.getUsername().equals(username));
            if (yaLogueado) {
                out.println("ERROR usuario ya logueado en otra sesion");
                log("Usuario ya logueado: " + username);
                return;
            }
            
            String token = UUID.randomUUID().toString();
            sesiones.put(token, user);
            out.println("OK " + token);
            log("✅ Usuario autenticado: " + username + " -> Token: " + token);
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