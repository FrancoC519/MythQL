package mythqlserver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

public class GestorConsultas {
    
    private final String dbPath = "Databases/";
    private Consumer<String> messageCallback;

    // Constructor para modo consola
    public GestorConsultas() {
        this.messageCallback = null;
    }

    // Constructor para modo UI
    public GestorConsultas(Consumer<String> callback) {
        this.messageCallback = callback;
    }

    public String procesarConsulta(String consulta, User user) {
        List<String> tokens = tokenizar(consulta);
        if (tokens.isEmpty()) {
            return "ERROR: Consulta vacía.";
        }

        String comando = tokens.get(0).toUpperCase();
        enviarMensaje("Comando recibido: " + comando);

        switch (comando) {
            case "SUMMON":
                return comandoSummon(tokens, user);
            case "UTILIZE":
                return comandoUtilize(tokens, user);
            case "BRING":
                return comandoBring(tokens, user);
            case "BURN":
                return comandoBurn(tokens, user);
            default:
                return "ERROR: Comando desconocido '" + comando + "'";
        }
    }

    private List<String> tokenizar(String consulta) {
        Pattern pattern = Pattern.compile("[A-Za-z0-9_]+|[{},(){}]");
        Matcher matcher = pattern.matcher(consulta);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group().toUpperCase());
        }
        return tokens;
    }

    private String comandoUtilize(List<String> tokens, User user) {
    if (tokens.size() != 2) {
        return "ERROR: Sintaxis UTILIZE inválida. Uso: UTILIZE <nombreDB>";
    }

    String base = tokens.get(1);
    File dbFile = new File(dbPath + base + ".csv");

    if (!dbFile.exists()) {
        return "ERROR: La base de datos '" + base + "' no existe.";
    }

    user.setBaseActiva(base);
    enviarMensaje("Base activa cambiada a: " + base);
    return "OK: Base de datos activa = " + base;
}

    private String comandoSummon(List<String> tokens, User user) {
        String msg;
        if (tokens.size() < 3) {
            msg = "ERROR: Sintaxis SUMMON inválida.";
            return msg;
        }

        CSVDatabaseManager db = new CSVDatabaseManager();

        if ("DATABASE".equals(tokens.get(1))) {
            String nombreDB = tokens.get(2);
            boolean exito = db.crearDatabase(nombreDB);
            msg = exito
                    ? "OK: Base de datos '" + nombreDB + "' creada."
                    : "ERROR: No se pudo crear la base de datos.";
            enviarMensaje(msg);
            return msg;
        }

        if ("TABLE".equals(tokens.get(1))) {
            if (user.getBaseActiva() == null) {
                return "ERROR: No hay base activa. Use UTILIZE <db> primero.";
            }

            File dbFile = new File(dbPath + user.getBaseActiva() + ".csv");
            if (!dbFile.exists()) {
                return "ERROR: La base activa '" + user.getBaseActiva() + "' no existe.";
            }

            String nombreTabla = tokens.get(2);
            List<String> atributos = new ArrayList<>();
            for (int i = 4; i < tokens.size(); i++) {
                if ("}".equals(tokens.get(i))) break;
                atributos.add(tokens.get(i));
            }

            boolean exito = db.crearTabla(user.getBaseActiva(), nombreTabla, atributos);
            msg = exito
                    ? "OK: Tabla '" + nombreTabla + "' creada en DB " + user.getBaseActiva()
                    : "ERROR: No se pudo crear la tabla.";
            enviarMensaje(msg);
            return msg;
        }

        msg = "ERROR: SUMMON debe ser DATABASE o TABLE.";
        enviarMensaje(msg);
        return msg;
    }

    private String comandoBring(List<String> tokens, User user) {
        String msg;
        if (tokens.size() < 2) {
            msg = "ERROR: Sintaxis BRING inválida.";
            return msg;
        }

        // BRING DATABASE <db>
        if ("DATABASE".equals(tokens.get(1)) && tokens.size() == 3) {
            String dbName = tokens.get(2);
            File dbFile = new File("Databases/" + dbName + ".csv");
            msg = dbFile.exists()
                    ? "OK: Base de datos '" + dbName + "' está disponible."
                    : "ERROR: Base de datos '" + dbName + "' no existe.";
            enviarMensaje(msg);
            return msg;
        }

        // BRING TABLE <table>
        if ("TABLE".equals(tokens.get(1)) && tokens.size() == 3) {
            if (user.getBaseActiva() == null) return "ERROR: No hay base activa.";
            String tableName = tokens.get(2);
            File tablaFile = new File("Databases/InfoDB/" + user.getBaseActiva() + "-" + tableName + ".csv");
            msg = tablaFile.exists()
                    ? "OK: Tabla '" + tableName + "' existe en DB '" + user.getBaseActiva() + "'."
                    : "ERROR: Tabla '" + tableName + "' no encontrada en DB '" + user.getBaseActiva() + "'.";
            enviarMensaje(msg);
            return msg;
        }
        
        msg = "ERROR: Sintaxis BRING inválida.";
        enviarMensaje(msg);
        return msg;
    }

    private String comandoBurn(List<String> tokens, User user) {
        if (tokens.size() < 3) {
            String msg = "ERROR: Sintaxis BURN inválida.";
            enviarMensaje(msg);
            return msg;
        }

        CSVDatabaseManager db = new CSVDatabaseManager();

        // BURN DATABASE <db>
        if ("DATABASE".equals(tokens.get(1))) {
            String dbName = tokens.get(2);
            boolean exito = db.eliminarDatabase(dbName);
            String msg = exito ? "OK: Base de datos '" + dbName + "' eliminada."
                               : "ERROR: No se pudo eliminar la base de datos.";
            enviarMensaje(msg);
            return msg;
        }

        // BURN TABLE <table>
        if ("TABLE".equals(tokens.get(1))) {
            if (user.getBaseActiva() == null) {
                String msg = "ERROR: No hay base activa.";
                enviarMensaje(msg);
                return msg;
            }
            String nombreTabla = tokens.get(2);
            boolean exito = db.eliminarTable(user.getBaseActiva(), nombreTabla);
            String msg = exito ? "OK: Tabla '" + nombreTabla + "' eliminada de DB '" + user.getBaseActiva() + "'."
                               : "ERROR: No se pudo eliminar la tabla.";
            enviarMensaje(msg);
            return msg;
        }

        return "ERROR: BURN debe ser DATABASE o TABLE.";
    }

    // Método unificado para mandar mensajes a UI o consola
    private void enviarMensaje(String msg) {
        if (messageCallback != null) {
            messageCallback.accept(msg);
        } else {
            System.out.println(msg);
        }
    }
}
