package mythqlserver;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class QueryProcessor {
    private Map<String, User> sesiones;
    private NotificationManager notificationManager;
    private GestorConsultas gestorConsultas;
    private Consumer<String> logCallback;
    private TransactionManager transactionManager;
    private UserStore userStore;

    public QueryProcessor(Map<String, User> sesiones, NotificationManager notificationManager, 
                         Consumer<String> logCallback, TransactionManager transactionManager) {
        this.sesiones = sesiones;
        this.notificationManager = notificationManager;
        this.logCallback = logCallback;
        this.transactionManager = transactionManager;
        this.userStore = new UserStore();
        
        // Si transactionManager es null, crear uno temporal para evitar NullPointerException
        if (this.transactionManager == null) {
            System.out.println("ADVERTENCIA: TransactionManager es null en QueryProcessor, creando uno temporal");
            this.transactionManager = new TransactionManager(logCallback);
        }
        
        this.gestorConsultas = new GestorConsultas(this::log, this::notificarCambioGlobal, this.transactionManager);
    }

    public void procesarQuery(String token, String consulta, PrintWriter out) {
        User user = sesiones.get(token);
        if (user == null) {
            out.println("ERROR sesion no valida");
            log("Intento de consulta con token inválido: " + token);
            return;
        }

        log("Consulta de " + user.getUsername() + ": " + consulta);
        
        // VERIFICAR PERMISOS ANTES DE EJECUTAR
        if (!tienePermisoParaConsulta(user, consulta)) {
            out.println("ERROR: Permiso denegado para ejecutar: " + consulta);
            log("Permiso denegado para " + user.getUsername() + ": " + consulta);
            return;
        }
        
        // VERIFICAR SI ES UN COMANDO DE TRANSACCIÓN
        String comando = consulta.trim().toUpperCase();
        
        if (comando.startsWith("START")) {
            manejarStart(token, user, out);
            return;
        } else if (comando.startsWith("SEAL")) {
            manejarSeal(token, user, out);
            return;
        } else if (comando.startsWith("UNDO")) {
            manejarUndo(token, consulta, user, out);
            return;
        }
        
        // Para comandos normales que modifican, hacer backup si hay transacción activa
        if (transactionManager != null && transactionManager.isTransactionActive(token) && 
            esConsultaQueModifica(consulta)) {
            
            String database = user.getBaseActiva();
            String table = extraerTablaDeConsulta(consulta);
            transactionManager.backupBeforeModification(token, database, table);
        }
        
        String resultado = gestorConsultas.procesarConsulta(consulta, user);
        out.println("RESULT " + resultado);
        log("Resultado: " + resultado);

        if (esConsultaQueModifica(consulta)) {
            String notificacion = "Cambio por " + user.getUsername() + ": " + extraerTipoCambio(consulta);
            log("Notificando: " + notificacion);
            notificationManager.broadcastNotificacion(notificacion);
        }
    }
    
    private boolean tienePermisoParaConsulta(User user, String consulta) {
        String comando = consulta.trim().toUpperCase().split(" ")[0];
        List<String> roles = user.getRoles();
        
        // Mapeo de comandos a permisos requeridos
        Map<String, List<String>> permisosRequeridos = Map.of(
            "BRING", List.of("BRING", "READER", "WRITER", "MANAGER", "ADMIN", "OWNER"),
            "FILE", List.of("FILE", "WRITER", "MANAGER", "ADMIN", "OWNER"),
            "REWRITE", List.of("REWRITE", "WRITER", "MANAGER", "ADMIN", "OWNER"),
            "SUMMON", List.of("SUMMON", "MANAGER", "ADMIN", "OWNER"),
            "BURN", List.of("BURN", "MANAGER", "ADMIN", "OWNER"),
            "MORPH", List.of("MORPH", "MANAGER", "ADMIN", "OWNER"),
            "SWEEP", List.of("SWEEP", "MANAGER", "ADMIN", "OWNER"),
            "INVOKE", List.of("INVOKE", "ADMIN", "OWNER"),
            "EMPOWER", List.of("EMPOWER", "ADMIN", "OWNER"),
            "DISARM", List.of("DISARM", "ADMIN", "OWNER")
        );
        
        List<String> permisosNecesarios = permisosRequeridos.getOrDefault(comando, List.of());
        
        // Si no hay permisos definidos para el comando, permitir por defecto
        if (permisosNecesarios.isEmpty()) {
            return true;
        }
        
        // Verificar si el usuario tiene al menos uno de los permisos requeridos
        for (String permiso : permisosNecesarios) {
            if (roles.contains(permiso.toUpperCase())) {
                return true;
            }
        }
        
        return false;
    }

    private void manejarStart(String token, User user, PrintWriter out) {
        if (transactionManager == null) {
            out.println("RESULT ERROR: Sistema de transacciones no disponible");
            log("ERROR: TransactionManager es null en manejarStart");
            return;
        }
        
        boolean success = transactionManager.startTransaction(token, user);
        if (success) {
            out.println("RESULT OK: Transaction started");
        } else {
            out.println("RESULT ERROR: No se pudo iniciar transaccion o ya hay una activa");
        }
    }
    
    private void manejarSeal(String token, User user, PrintWriter out) {
        if (transactionManager == null) {
            out.println("RESULT ERROR: Sistema de transacciones no disponible");
            log("ERROR: TransactionManager es null en manejarSeal");
            return;
        }
        
        boolean success = transactionManager.sealTransaction(token);
        if (success) {
            out.println("RESULT OK: Transaction sealed");
        } else {
            out.println("RESULT ERROR: No hay transaccion activa para seal");
        }
    }
    
    private void manejarUndo(String token, String consulta, User user, PrintWriter out) {
        if (transactionManager == null) {
            out.println("RESULT ERROR: Sistema de transacciones no disponible");
            log("ERROR: TransactionManager es null en manejarUndo");
            return;
        }
        
        // Extraer número de UNDO si existe
        String undoNumber = null;
        String[] partes = consulta.trim().split("\\s+");
        if (partes.length > 1) {
            undoNumber = partes[1];
            // Verificar que sea un número válido
            if (!undoNumber.matches("\\d+")) {
                undoNumber = null;
            }
        }
        
        boolean success = transactionManager.undoTransaction(token, undoNumber);
        if (success) {
            out.println("RESULT OK: Undo executed");
        } else {
            out.println("RESULT ERROR: No se pudo ejecutar undo");
        }
    }
    
    private String extraerTablaDeConsulta(String consulta) {
        String[] tokens = consulta.trim().split("\\s+");
        if (tokens.length < 2) return null;
        
        String comando = tokens[0].toUpperCase();
        switch (comando) {
            case "SUMMON":
                if (tokens.length > 2 && "TABLE".equals(tokens[1].toUpperCase())) {
                    return tokens[2];
                }
                break;
            case "BURN":
                if (tokens.length > 2 && "TABLE".equals(tokens[1].toUpperCase())) {
                    return tokens[2];
                }
                break;
            case "FILE":
                if (tokens.length > 1) {
                    return tokens[1];
                }
                break;
            case "MORPH":
                if (tokens.length > 1) {
                    return tokens[1];
                }
                break;
            case "SWEEP":
                if (tokens.length > 1) {
                    return tokens[1];
                }
                break;
            case "REWRITE":
                if (tokens.length > 1) {
                    return tokens[1];
                }
                break;
        }
        return null;
    }

    public String obtenerEsquemasJerarquicos() {
        log("Solicitando esquemas jerárquicos");
        return gestorConsultas.obtenerEsquemasJerarquicos();
    }

    private void notificarCambioGlobal(String mensaje) {
        notificationManager.broadcastNotificacion(mensaje);
    }

    private void log(String msg) {
        if (logCallback != null) logCallback.accept(msg);
        else System.out.println(msg);
    }

    private boolean esConsultaQueModifica(String consulta) {
        String comando = consulta.trim().toUpperCase();
        return comando.startsWith("SUMMON") ||
               comando.startsWith("BURN") ||
               comando.startsWith("FILE") ||
               comando.startsWith("MORPH") ||
               comando.startsWith("SWEEP") ||
               comando.startsWith("REWRITE");
    }

    private String extraerTipoCambio(String consulta) {
        String[] partes = consulta.trim().split("\\s+");
        if (partes.length < 2) return consulta;
        String comando = partes[0].toUpperCase();
        String objeto = partes[1].toUpperCase();

        switch (comando) {
            case "SUMMON": return "Creación: " + objeto;
            case "BURN": return "Eliminación: " + objeto;
            case "FILE": return "Inserción de datos";
            case "MORPH": return "Alteración de tabla " + objeto;
            case "SWEEP": return "Limpieza de tabla " + objeto;
            case "REWRITE": return "Actualización de registros en " + objeto;
            default: return comando + " ejecutado";
        }
    }
}