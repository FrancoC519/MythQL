/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mythqlserver;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GestorConsultas {

    public String procesarConsulta(String consulta) {
        List<String> tokens = tokenizar(consulta);

        if (tokens.isEmpty()) {
            return "ERROR: Consulta vacía.";
        }

        String comando = tokens.get(0).toUpperCase();

        switch (comando) {
            case "SUMMON":
                return comandoSummon(tokens);
            default:
                return "ERROR: Comando desconocido '" + comando + "'";
        }
    }

    private List<String> tokenizar(String consulta) {
        Pattern pattern = Pattern.compile("[A-Za-z0-9_]+|[{},(){}]");
        Matcher matcher = pattern.matcher(consulta);

        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private String comandoSummon(List<String> tokens) {
        // Formato esperado:
        // SUMMON TABLE nombreBase{atributos}
        if (tokens.size() < 4) {
            return "ERROR: Sintaxis SUMMON TABLE inválida.";
        }

        if (!"TABLE".equalsIgnoreCase(tokens.get(1))) {
            return "ERROR: Se esperaba TABLE después de SUMMON.";
        }

        String nombreTabla = tokens.get(2);
        if (!"{".equals(tokens.get(3))) {
            return "ERROR: Falta '{' después del nombre de la tabla.";
        }

        // Extraer atributos hasta "}"
        List<String> atributos = new ArrayList<>();
        for (int i = 4; i < tokens.size(); i++) {
            if ("}".equals(tokens.get(i))) {
                break;
            }
            atributos.add(tokens.get(i));
        }

        // Llamamos al gestor de CSV para guardar la tabla
        CSVDatabaseManager db = new CSVDatabaseManager();
        boolean exito = db.crearTabla(nombreTabla, atributos);

        if (exito) {
            return "OK: Tabla '" + nombreTabla + "' creada con atributos " + atributos;
        } else {
            return "ERROR: No se pudo crear la tabla.";
        }
    }
}