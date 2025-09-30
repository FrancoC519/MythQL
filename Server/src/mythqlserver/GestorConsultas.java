package mythqlserver;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GestorConsultas {

    private static String baseActiva = null;

    public String procesarConsulta(String consulta) {
        List<String> tokens = tokenizar(consulta);

        if (tokens.isEmpty()) {
            return "ERROR: Consulta vacía.";
        }

        String comando = tokens.get(0).toUpperCase();
        System.out.println(comando);
        switch (comando) {
            case "SUMMON":
                return comandoSummon(tokens);
            case "UTILIZE":
                return comandoUtilize(tokens);
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

    private String comandoUtilize(List<String> tokens) {
        if (tokens.size() != 2) {
            return "ERROR: Sintaxis UTILIZE inválida. Uso: UTILIZE <nombreDB>";
        }
        baseActiva = tokens.get(1);
        return "OK: Base de datos activa = " + baseActiva;
    }

    private String comandoSummon(List<String> tokens) {
        if (tokens.size() < 3) {
            return "ERROR: Sintaxis SUMMON inválida.";
        }

        // SUMMON DATABASE
        if ("DATABASE".equals(tokens.get(1))) {
            String nombreDB = tokens.get(2);
            CSVDatabaseManager db = new CSVDatabaseManager();
            boolean exito = db.crearDatabase(nombreDB);
            return exito ? "OK: Base de datos '" + nombreDB + "' creada."
                         : "ERROR: No se pudo crear la base de datos.";
        }

        // SUMMON TABLE
        if ("TABLE".equals(tokens.get(1))) {
            if (baseActiva == null) {
                return "ERROR: No hay base activa. Use UTILIZE <db> primero.";
            }
            String nombreTabla = tokens.get(2);
            List<String> atributos = new ArrayList<>();
            for (int i = 4; i < tokens.size(); i++) {
                if ("}".equals(tokens.get(i))) break;
                atributos.add(tokens.get(i));
            }
            CSVDatabaseManager db = new CSVDatabaseManager();
            boolean exito = db.crearTabla(baseActiva, nombreTabla, atributos);
            return exito ? "OK: Tabla '" + nombreTabla + "' creada en DB " + baseActiva
                         : "ERROR: No se pudo crear la tabla.";
        }

        return "ERROR: SUMMON debe ser DATABASE o TABLE.";
    }
}
