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
            System.out.println("TOKEN: " + matcher.group());
            tokens.add(matcher.group());
        }
        
        String comando = tokens.get(0);

        switch (comando) {
            case "SUMMON":
                return comandoSummon(tokens);
            case "BURN":
                return comandoBurn(tokens);
            case "BRING":
                return comandoBring(tokens);
            default:
                System.out.println("Comando desconocido: " + comando);
                return false;
        }
    }
    
    // ========== SUMMON ==========
    public Boolean comandoSummon(List<String> tokens) {
        int i = 0;

        if (tokens.size() < 5) {
            return error("Sintaxis incompleta en SUMMON");
        }

        if (!"SUMMON".equals(tokens.get(i++))) return error("Falta SUMMON");
        if (!"TABLE".equals(tokens.get(i++))) return error("Falta TABLE");

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

    // ========== BURN ==========
    public Boolean comandoBurn(List<String> tokens) {
        if (tokens.size() != 3) {
            return error("Sintaxis incorrecta en BURN. Uso correcto: BURN TABLE <nombreTabla>");
        }

        if (!"BURN".equals(tokens.get(0))) return error("Falta BURN");
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

        // Leer columnas
        while (i < tokens.size() && !"}".equals(tokens.get(i))) {
            String columna = tokens.get(i++);
            if (!columna.matches("[A-Za-z_][A-Za-z0-9_()*]+")) {
                return error("Columna inválida en BRING: " + columna);
            }
            if (",".equals(tokens.get(i))) {
                i++;
            }
        }

        if (i >= tokens.size() || !"}".equals(tokens.get(i++)))
            return error("Falta '}' en BRING");

        // Clausulas opcionales (CONDITION, CLUSTER, ALIGN, DECLINE, CAP)
        while (i < tokens.size()) {
            String token = tokens.get(i++);
            switch (token) {
                case "CONDITION":
                    System.out.println("Cláusula CONDITION detectada");
                    break;
                case "CLUSTER":
                    System.out.println("Cláusula CLUSTER detectada");
                    break;
                case "ALIGN":
                    System.out.println("Cláusula ALIGN detectada");
                    break;
                case "DECLINE":
                    System.out.println("Cláusula DECLINE detectada");
                    break;
                case "CAP":
                    System.out.println("Cláusula CAP detectada");
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
