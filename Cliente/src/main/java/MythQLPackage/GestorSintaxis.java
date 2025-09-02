package MythQLPackage;

public class GestorSintaxis {
    
    public Boolean enviarConsulta(String consulta){

        String[] tokens = consulta.split(" ");

        for (String t : tokens) {
            if (!t.trim().isEmpty()) {
            System.out.println("TOKEN: " + t.trim());
            }
        }
        
        if (!tokens[0].equals("SUMMON") || !tokens[1].equals("TABLE")) {
            throw new IllegalArgumentException("El comando debe empezar con SUMMON TABLE");
        }

        for (int i = 2; i < tokens.length; i++){
            //Estructura basica de comando SUMMON:
            //SUMMON TABLE invoices{invoices_id INT SELF STACKABLE, customer VARCHAR(25)}
            //[0]SUMMON [1]TABLE [2]invoices [3]{ [4]invoices_id [5]INT [6]SELF [8]STACKABLE [9], [10]customer [11]VARCHAR [12]( [13]25 [14]) [15]}
            
            if (tokens[i].equals(tokens[2])){
                String[] part = tokens[2].split("{");
                
            }
                
        }
        
        return true;
    }
}
