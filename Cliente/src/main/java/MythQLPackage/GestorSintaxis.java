package MythQLPackage;

public class GestorSintaxis {
    
    public Boolean enviarConsulta(String consulta){
        
        String regex = "[A-Za-z_][A-Za-z0-9_]*|\\{|\\}|\\(|\\)|,|[0-9]+";

        String[] tokens = consulta.split("(?=\\{|\\}|\\(|\\)|,)|(?<=\\{|\\}|\\(|\\)|,)");

        for (String t : tokens) {
            if (!t.trim().isEmpty()) {
            System.out.println("TOKEN: " + t.trim());
            }
        }
        
        if (!tokens[0].equals("SUMMON") || !tokens[1].equals("TABLE")) {
            throw new IllegalArgumentException("El comando debe empezar con SUMMON TABLE");
        }

        // Después chequeás que haya un identificador (nombre de tabla)
        String nombreTabla = tokens[2];

        // Y que el siguiente token sea "{"
        if (!tokens[3].equals("{")) {
            throw new IllegalArgumentException("Falta '{' después del nombre de tabla");
        }

        // Luego recorrer los campos hasta encontrar "}"
        
        return true;
    }
}
