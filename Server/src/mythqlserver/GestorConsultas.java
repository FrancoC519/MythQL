package mythqlserver;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GestorConsultas {

    public String procesarConsulta(String consulta, User user) {
        List<String> tokens = tokenizar(consulta);
        if (tokens.isEmpty()) {
            return "ERROR: Consulta vacía.";
        }

        String comando = tokens.get(0).toUpperCase();
        System.out.println("Comando recibido: " + comando);

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
        user.setBaseActiva(base);
        return "OK: Base de datos activa = " + base;
    }

    private String comandoSummon(List<String> tokens, User user) {
        if (tokens.size() < 3) {
            return "ERROR: Sintaxis SUMMON inválida.";
        }

        CSVDatabaseManager db = new CSVDatabaseManager();

        // SUMMON DATABASE
        if ("DATABASE".equals(tokens.get(1))) {
            String nombreDB = tokens.get(2);
            boolean exito = db.crearDatabase(nombreDB);
            return exito ? "OK: Base de datos '" + nombreDB + "' creada."
                         : "ERROR: No se pudo crear la base de datos.";
        }

        // SUMMON TABLE
        if ("TABLE".equals(tokens.get(1))) {
            if (user.getBaseActiva() == null) {
                return "ERROR: No hay base activa. Use UTILIZE <db> primero.";
            }
            String nombreTabla = tokens.get(2);
            List<String> atributos = new ArrayList<>();
            for (int i = 4; i < tokens.size(); i++) {
                if ("}".equals(tokens.get(i))) break;
                atributos.add(tokens.get(i));
            }
            boolean exito = db.crearTabla(user.getBaseActiva(), nombreTabla, atributos);
            return exito ? "OK: Tabla '" + nombreTabla + "' creada en DB " + user.getBaseActiva()
                         : "ERROR: No se pudo crear la tabla.";
        }
        return "ERROR: SUMMON debe ser DATABASE o TABLE.";
    }

    private String comandoBring(List<String> tokens, User user) {
        if (tokens.size() < 2) {
            return "ERROR: Sintaxis BRING inválida.";
        }

        // BRING DATABASE <db>
        if ("DATABASE".equals(tokens.get(1)) && tokens.size() == 3) {
            String dbName = tokens.get(2);
            File dbFile = new File("Databases/" + dbName + ".csv");
            return dbFile.exists()
                    ? "OK: Base de datos '" + dbName + "' está disponible."
                    : "ERROR: Base de datos '" + dbName + "' no existe.";
        }

        // BRING TABLE <table>
        if ("TABLE".equals(tokens.get(1)) && tokens.size() == 3) {
            if (user.getBaseActiva() == null) return "ERROR: No hay base activa.";
            String tableName = tokens.get(2);
            File tablaFile = new File("Databases/InfoDB/" + user.getBaseActiva() + "-" + tableName + ".csv");
            return tablaFile.exists()
                    ? "OK: Tabla '" + tableName + "' existe en DB '" + user.getBaseActiva() + "'."
                    : "ERROR: Tabla '" + tableName + "' no encontrada en DB '" + user.getBaseActiva() + "'.";
        }

        return "ERROR: Sintaxis BRING inválida.";
    }

    private String comandoBurn(List<String> tokens, User user) {
        if (tokens.size() < 3) {
            return "ERROR: Sintaxis BURN inválida.";
        }

        CSVDatabaseManager db = new CSVDatabaseManager();

        // BURN DATABASE <db>
        if ("DATABASE".equals(tokens.get(1))) {
            String dbName = tokens.get(2);
            boolean exito = db.eliminarDatabase(dbName);
            return exito ? "OK: Base de datos '" + dbName + "' eliminada."
                         : "ERROR: No se pudo eliminar la base de datos.";
        }

        // BURN TABLE <table>
        if ("TABLE".equals(tokens.get(1))) {
            if (user.getBaseActiva() == null) return "ERROR: No hay base activa.";
            String nombreTabla = tokens.get(2);
            boolean exito = db.eliminarTable(user.getBaseActiva(), nombreTabla);
            return exito ? "OK: Tabla '" + nombreTabla + "' eliminada de DB '" + user.getBaseActiva() + "'."
                         : "ERROR: No se pudo eliminar la tabla.";
        }

        return "ERROR: BURN debe ser DATABASE o TABLE.";
    }
}