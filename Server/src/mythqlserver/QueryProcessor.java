package mythqlserver;

import java.io.PrintWriter;
import java.util.Map;
import java.util.function.Consumer;

public class QueryProcessor {
    private Map<String, User> sesiones;
    private NotificationManager notificationManager;
    private GestorConsultas gestorConsultas;
    private Consumer<String> logCallback;
    
    public QueryProcessor(Map<String, User> sesiones, NotificationManager notificationManager, Consumer<String> logCallback) {
        this.sesiones = sesiones;
        this.notificationManager = notificationManager;
        this.logCallback = logCallback;
        this.gestorConsultas = new GestorConsultas(
            this::log,  // ← Pasar callback de logging
            this::notificarCambioGlobal
        );
    }
    
    public void procesarQuery(String token, String consulta, PrintWriter out) {
        User user = sesiones.get(token);
        if (user == null) {
            out.println("ERROR sesion no valida");
            log("❌ Intento de consulta con token inválido: " + token);
            return;
        }

        log("🔍 Consulta de " + user.getUsername() + ": " + consulta);
        String resultado = gestorConsultas.procesarConsulta(consulta, user);
        out.println("RESULT " + resultado);
        log("📝 Resultado: " + resultado);
        
        if (esConsultaQueModifica(consulta)) {
            String notificacion = "Cambio por " + user.getUsername() + ": " + 
                                extraerTipoCambio(consulta);
            log("📢 Notificando: " + notificacion);
            notificationManager.broadcastNotificacion(notificacion);
        }
    }
    
    public String obtenerEsquemasJerarquicos() {
        log("📊 Solicitando esquemas jerárquicos");
        return gestorConsultas.obtenerEsquemasJerarquicos();
    }
    
    private void notificarCambioGlobal(String mensaje) {
        notificationManager.broadcastNotificacion(mensaje);
    }
    
    private void log(String msg) {
        if (logCallback != null) {
            logCallback.accept(msg);
        } else {
            System.out.println(msg);
        }
    }
    
    private boolean esConsultaQueModifica(String consulta) {
        String comando = consulta.trim().toUpperCase();
        return comando.startsWith("SUMMON") || 
               comando.startsWith("BURN") || 
               comando.startsWith("FILE");
    }
    
    private String extraerTipoCambio(String consulta) {
        String[] partes = consulta.trim().split("\\s+");
        if (partes.length < 2) return consulta;
        
        String comando = partes[0].toUpperCase();
        String objeto = partes[1].toUpperCase();
        
        switch (comando) {
            case "SUMMON":
                return "Creación: " + objeto + " " + (partes.length > 2 ? partes[2] : "");
            case "BURN":
                return "Eliminación: " + objeto + " " + (partes.length > 2 ? partes[2] : "");
            case "FILE":
                return "Inserción en tabla";
            default:
                return comando + " ejecutado";
        }
    }
}