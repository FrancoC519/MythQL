package MythQLPackage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GestorSintaxis {
    
    public Boolean enviarConsulta(String consulta){
        Pattern pattern = Pattern.compile("[A-Za-z0-9_]+|[{},(){}]");
        Matcher matcher = pattern.matcher(consulta);
        List<String> tokens = new ArrayList<>();
        
        while (matcher.find()) {
            String token = matcher.group();
            tokens.add(token.toUpperCase()); // case-insensitive
            System.out.println("TOKEN: " + token);
        }
        
        String comando = tokens.get(0);
        switch (comando) {
            case "SUMMON":
                return comandoSummon(tokens);
            case "BURN":
                return comandoBurn(tokens);
            case "BRING":
                return comandoBring(tokens);
            case "UTILIZE":
                return comandoUtilize(tokens);
            default:
                System.out.println("Comando desconocido: " + comando);
                return false;
        }
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

    // ========== SUMMON ==========
    public Boolean comandoSummon(List<String> tokens) {
        if (tokens.size() < 3) {
            return error("Sintaxis incompleta en SUMMON");
        }

        // SUMMON DATABASE
        if ("DATABASE".equals(tokens.get(1))) {
            if (tokens.size() != 3) return error("Uso: SUMMON DATABASE <nombreDB>");
            String nombreDB = tokens.get(2);
            if (!nombreDB.matches("[A-Za-z_][A-Za-z0-9_]*"))
                return error("Nombre de base de datos inválido");
            System.out.println("Comando SUMMON DATABASE detectado: " + nombreDB);
            return true;
        }

        // SUMMON TABLE
        if ("TABLE".equals(tokens.get(1))) {
            int i = 2;
            String nombreTabla = tokens.get(i++);
            if (!nombreTabla.matches("[A-Za-z_][A-Za-z0-9_]*"))
                return error("Nombre de tabla inválido");

            if (!"{".equals(tokens.get(i++))) return error("Falta '{'");

            while (i < tokens.size()) {
                if ("}".equals(tokens.get(i))) {
                    return true;
                }

                String nombreColumna = tokens.get(i++);
                if (!nombreColumna.matches("[A-Za-z_][A-Za-z0-9_]*"))
                    return error("Nombre de columna inválido: " + nombreColumna);

                String tipo = tokens.get(i++);
                switch (tipo) {
                    case "INT":
                        if (i + 1 < tokens.size()
                                && "SELF".equals(tokens.get(i))
                                && "STACKABLE".equals(tokens.get(i + 1))) {
                            System.out.println("Columna INT con SELF STACKABLE detectada: " + nombreColumna);
                            i += 2;
                        } else {
                            System.out.println("Columna INT detectada: " + nombreColumna);
                        }
                        break;

                    case "VARCHAR":
                        if (!"(".equals(tokens.get(i++))) return error("Falta '(' en VARCHAR");
                        String size = tokens.get(i++);
                        if (!size.matches("\\d+")) return error("Tamaño de VARCHAR inválido: " + size);
                        if (!")".equals(tokens.get(i++))) return error("Falta ')' en VARCHAR");
                        System.out.println("Columna VARCHAR(" + size + ") detectada: " + nombreColumna);
                        break;

                    default:
                        return error("Tipo de dato no soportado: " + tipo);
                }

                if (",".equals(tokens.get(i))) {
                    i++;
                    continue;
                }
            }
            return error("Falta '}' de cierre en SUMMON");
        }

        return error("Se esperaba DATABASE o TABLE después de SUMMON");
    }

    // ========== BURN ==========
    public Boolean comandoBurn(List<String> tokens) {
        if (tokens.size() != 3) {
            return error("Sintaxis incorrecta en BURN. Uso correcto: BURN TABLE <nombreTabla>");
        }
        if (!"TABLE".equals(tokens.get(1))) return error("Falta TABLE");
        String nombreTabla = tokens.get(2);
        if (!nombreTabla.matches("[A-Za-z_][A-Za-z0-9_]*"))
            return error("Nombre de tabla inválido en BURN: " + nombreTabla);

        System.out.println("Comando BURN detectado sobre la tabla: " + nombreTabla);
        return true;
    }

    // ========== BRING ==========
    public Boolean comandoBring(List<String> tokens) {
        int i = 0;
        if (!"BRING".equals(tokens.get(i++))) return error("Falta BRING");

        String nombreTabla = tokens.get(i++);
        if (!nombreTabla.matches("[A-Za-z_][A-Za-z0-9_]*"))
            return error("Nombre de tabla inválido en BRING: " + nombreTabla);

        if (!"{".equals(tokens.get(i++))) return error("Falta '{' en BRING");

        while (i < tokens.size() && !"}".equals(tokens.get(i))) {
            String columna = tokens.get(i++);
            if (!columna.matches("[A-Za-z_][A-Za-z0-9_()*]+")) {
                return error("Columna inválida en BRING: " + columna);
            }
            if (",".equals(tokens.get(i))) {
                i++;
            }
        }

        if (i >= tokens.size() || !"}".equals(tokens.get(i++))) return error("Falta '}' en BRING");

        while (i < tokens.size()) {
            String token = tokens.get(i++);
            switch (token) {
                case "CONDITION":
                case "CLUSTER":
                case "ALIGN":
                case "DECLINE":
                case "CAP":
                    System.out.println("Cláusula " + token + " detectada");
                    break;
                default:
                    return error("Token inesperado en BRING: " + token);
            }
        }

        System.out.println("Comando BRING válido sobre tabla: " + nombreTabla);
        return true;
    }

    // ========== ERROR ==========
    private boolean error(String msg) {
        System.out.println("Error de sintaxis: " + msg);
        return false;
    }
}
