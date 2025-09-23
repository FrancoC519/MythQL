package mythqlserver;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class CSVDatabaseManager {

    public boolean crearTabla(String nombreTabla, List<String> atributos) {
        try (FileWriter writer = new FileWriter(nombreTabla + ".csv")) {
            // Escribir encabezado con atributos separados por coma
            for (int i = 0; i < atributos.size(); i++) {
                writer.write(atributos.get(i));
                if (i < atributos.size() - 1) {
                    writer.write(",");
                }
            }
            writer.write("\n");
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
