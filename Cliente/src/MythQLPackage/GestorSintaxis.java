package MythQLPackage;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GestorSintaxis {
    MythQL_UI UI;
    public GestorSintaxis(MythQL_UI MQLUI) {
        this.UI = MQLUI;
    }
    
    public Boolean enviarConsulta(String consulta) {
        List<String> tokens = tokenizar(consulta);

        if (tokens.isEmpty()) {
            System.out.println("No se detectaron tokens válidos.");
            return false;
        }

        System.out.println("TOKENS DETECTADOS:");
        for (String t : tokens) System.out.println("TOKEN: " + t);

        String comando = tokens.get(0).toUpperCase();

        switch (comando) {
            case "SUMMON":   return comandoSummon(tokens);
            case "BURN":     return comandoBurn(tokens);
            case "BRING":    return comandoBring(tokens);
            case "UTILIZE":  return comandoUtilize(tokens);
            case "LOGOUT":   return comandoLOGOUT(tokens);
            case "MANIFEST": return comandoManifest(tokens);
            case "DEPICT":   return comandoDepict(tokens);
            case "FILE":     return comandoFile(tokens);
            default:
                System.out.println("Comando desconocido: " + comando);
                return false;
        }
    }
    // ========== TOKENIZADOR NUEVO ==========
    private List<String> tokenizar(String consulta) {
        List<String> tokens = new ArrayList<>();
        Pattern pattern = Pattern.compile(
            "'[^']*'|\"[^\"]*\"|\\d+\\.\\d+|\\d+|[A-Za-z_][A-Za-z0-9_]*|[{}\\[\\](),;]"
        );
        Matcher matcher = pattern.matcher(consulta);

        while (matcher.find()) {
            String token = matcher.group();
            // Mantener los valores literales tal como están (sin upper)
            if (token.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                tokens.add(token.toUpperCase());
            } else {
                tokens.add(token);
            }
        }
        return tokens;
    }

    // ========== UTILIZE ==========
    public Boolean comandoUtilize(List<String> tokens) {
        if (tokens.size() != 2) {
            return error("Sintaxis incorrecta en UTILIZE. Uso correcto: UTILIZE <nombreDB>");
        }
        String nombreDB = tokens.get(1);
        if (!nombreDB.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return error("Nombre de base de datos inválido: " + nombreDB);
        }
        System.out.println("Base de datos activa: " + nombreDB);
        return true;
    }
    
    // ========== LOGOUT ==========
    public Boolean comandoLOGOUT(List<String> tokens) {
        return true;
    }
    
    // ========== SUMMON ==========
    public Boolean comandoSummon(List<String> tokens) {
        if (tokens.size() < 3) {
            return error("Sintaxis incompleta en SUMMON");
        }

        if ("DATABASE".equals(tokens.get(1))) {
            if (tokens.size() != 3) return error("Uso: SUMMON DATABASE <nombreDB>");
            String nombreDB = tokens.get(2);
            if (!nombreDB.matches("[A-Za-z_][A-Za-z0-9_]*"))
                return error("Nombre de base de datos inválido");
            System.out.println("Comando SUMMON DATABASE detectado: " + nombreDB);
            return true;
        }

        if ("TABLE".equals(tokens.get(1))) {
            int i = 2;
            String nombreTabla = tokens.get(i++);
            if (!nombreTabla.matches("[A-Za-z_][A-Za-z0-9_]*"))
                return error("Nombre de tabla inválido");
            if (!"{".equals(tokens.get(i++))) return error("Falta '{'");
            while (i < tokens.size()) {
                if ("}".equals(tokens.get(i))) return true;
                String nombreColumna = tokens.get(i++);
                if (!nombreColumna.matches("[A-Za-z_][A-Za-z0-9_]*"))
                    return error("Nombre de columna inválido: " + nombreColumna);
                String tipo = tokens.get(i++);
                switch (tipo) {
                    case "INT":
                        if (i + 1 < tokens.size()
                                && "SELF".equals(tokens.get(i))
                                && "STACKABLE".equals(tokens.get(i + 1))) {
                            System.out.println("Columna INT con SELF STACKABLE: " + nombreColumna);
                            i += 2;
                        } else {
                            System.out.println("Columna INT: " + nombreColumna);
                        }
                        break;
                    case "VARCHAR":
                        if (!"(".equals(tokens.get(i++))) return error("Falta '(' en VARCHAR");
                        String size = tokens.get(i++);
                        if (!size.matches("\\d+")) return error("Tamaño de VARCHAR inválido: " + size);
                        if (!")".equals(tokens.get(i++))) return error("Falta ')' en VARCHAR");
                        System.out.println("Columna VARCHAR(" + size + "): " + nombreColumna);
                        break;
                    default:
                        return error("Tipo de dato no soportado: " + tipo);
                }
                if (",".equals(tokens.get(i))) i++;
            }
            return error("Falta '}' de cierre en SUMMON");
        }

        return error("Se esperaba DATABASE o TABLE después de SUMMON");
    }

    // ========== BURN ==========
    public Boolean comandoBurn(List<String> tokens) {
        if (tokens.size() != 3) {
            return error("Sintaxis incorrecta en BURN. Uso: BURN DATABASE <db> o BURN TABLE <table>");
        }
        String tipo = tokens.get(1);
        String nombre = tokens.get(2);
        if ("TABLE".equals(tipo)) {
            if (!nombre.matches("[A-Za-z_][A-Za-z0-9_]*")) return error("Nombre de tabla inválido");
            System.out.println("Comando BURN TABLE: " + nombre);
            return true;
        } else if ("DATABASE".equals(tipo)) {
            if (!nombre.matches("[A-Za-z_][A-Za-z0-9_]*")) return error("Nombre DB inválido");
            System.out.println("Comando BURN DATABASE: " + nombre);
            return true;
        }
        return error("BURN debe ser seguido de DATABASE o TABLE.");
    }

    // ========== BRING ==========
    public Boolean comandoBring(List<String> tokens) {
        if (tokens.size() < 2)
            return error("Sintaxis incorrecta. Uso: BRING <tabla> [ { columnas } ]");

        String nombreTabla = tokens.get(1);
        if (!nombreTabla.matches("[A-Za-z_][A-Za-z0-9_]*"))
            return error("Nombre de tabla inválido: " + nombreTabla);

        // Solo BRING <tabla>
        if (tokens.size() == 2) {
            System.out.println("Comando BRING completo: " + nombreTabla + " (todas las columnas)");
            return true;
        }

        // BRING <tabla> { col1, col2 }
        int i = 2;
        if (!"{".equals(tokens.get(i++)))
            return error("Se esperaba '{' tras el nombre de la tabla.");

        List<String> columnas = new ArrayList<>();
        while (i < tokens.size() && !"}".equals(tokens.get(i))) {
            String col = tokens.get(i++);
            if (!col.matches("[A-Za-z_][A-Za-z0-9_]*"))
                return error("Nombre de columna inválido: " + col);
            columnas.add(col);
            if (i < tokens.size() && ",".equals(tokens.get(i))) i++;
        }

        if (i >= tokens.size() || !"}".equals(tokens.get(i)))
            return error("Falta '}' de cierre en la selección de columnas.");

        System.out.println("Comando BRING con columnas: " + nombreTabla + " → " + columnas);
        return true;
    }

    // ========== MANIFEST ==========
    public Boolean comandoManifest(List<String> tokens) {
        if (tokens.size() != 2) {
            return error("Sintaxis incorrecta en MANIFEST. Uso: MANIFEST DATABASES | MANIFEST TABLES");
        }
        if ("DATABASES".equals(tokens.get(1)) || "TABLES".equals(tokens.get(1))) {
            System.out.println("Comando MANIFEST: " + tokens.get(1));
            return true;
        }
        return error("MANIFEST debe ir seguido de DATABASES o TABLES");
    }

    // ========== DEPICT ==========
    public Boolean comandoDepict(List<String> tokens) {
        if (tokens.size() != 2) {
            return error("Sintaxis incorrecta en DEPICT. Uso: DEPICT <tabla>");
        }
        String nombreTabla = tokens.get(1);
        if (!nombreTabla.matches("[A-Za-z_][A-Za-z0-9_]*"))
            return error("Nombre de tabla inválido en DEPICT: " + nombreTabla);
        System.out.println("Comando DEPICT sobre la tabla: " + nombreTabla);
        return true;
    }
    
    // ========== FILE ==========
    public Boolean comandoFile(List<String> tokens) {
        if (tokens.size() < 4)
            return error("Sintaxis FILE incompleta. Uso: FILE <tabla>{<columnas>} [<valores>], ...");

        int i = 1;
        String nombreTabla = tokens.get(i++);
        if (!nombreTabla.matches("[A-Za-z_][A-Za-z0-9_]*"))
            return error("Nombre de tabla inválido: " + nombreTabla);

        if (!"{".equals(tokens.get(i++))) return error("Falta '{' tras el nombre de la tabla.");

        List<String> columnas = new ArrayList<>();
        while (i < tokens.size() && !"}".equals(tokens.get(i))) {
            String col = tokens.get(i++);
            if (!col.matches("[A-Za-z_][A-Za-z0-9_]*"))
                return error("Nombre de columna inválido: " + col);
            columnas.add(col);
            if (",".equals(tokens.get(i))) i++;
        }
        if (i >= tokens.size() || !"}".equals(tokens.get(i++)))
            return error("Falta '}' de cierre en las columnas.");

        if (i >= tokens.size()) return error("Faltan registros entre corchetes [ ].");

        while (i < tokens.size()) {
            if (!"[".equals(tokens.get(i++))) return error("Se esperaba '[' para abrir un registro.");
            List<String> valores = new ArrayList<>();
            while (i < tokens.size() && !"]".equals(tokens.get(i))) {
                String val = tokens.get(i++);
                valores.add(val);
                if (",".equals(tokens.get(i))) i++;
            }
            if (i >= tokens.size() || !"]".equals(tokens.get(i++)))
                return error("Falta ']' de cierre en un registro.");
            if (valores.size() > columnas.size())
                return error("Demasiados valores en un registro.");
            if (i < tokens.size() && ",".equals(tokens.get(i))) i++;
        }

        System.out.println("Comando FILE válido sobre tabla " + nombreTabla + " con columnas " + columnas);
        return true;
    }

    // ========== ERROR ==========
    private boolean error(String msg) {
        UI.logError("Error de sintaxis:" + msg);
        System.out.println("Error de sintaxis: " + msg);
        
        return false;
    }
}
