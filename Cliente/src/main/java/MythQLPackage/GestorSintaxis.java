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
        
        
        //Estructura de un comando SUMMON:
        //[SUMMON, TABLE, invoices, {, invoices_id, INT, SELF, STACKABLE, ,, customer, VARCHAR, (, 25, ), }]
        int comando = 0;
        switch(comando){
            case 0:
                if ("SUMMON".equals(tokens.get(0))){
                    if (comandoSummon(tokens) == true){
                        return true;
                    }
            }
            case 1:
                if(!"SUMMON".equals(tokens.get(0))){
                   System.out.println("<Place holder>Comando desconocido: Esta versión del programa solamente soporta comandos 'SUMMON'.");
                   return false;
                }
        }
        
        return false;
    }
    
    @SuppressWarnings("unchecked")
    public Boolean comandoSummon(List<String> tokens) {
        int i = 0;

        if (tokens.size() < 5) {
            System.out.println("Error: sintaxis incompleta.");
            return false;
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

    return error("Falta '}' de cierre");
}

    private boolean error(String msg) {
        System.out.println("Error de sintaxis: " + msg);
        return false;
    }
}
