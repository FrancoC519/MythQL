package mythqlserver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class CSVDatabaseManager {

    private final String dbPath = "Databases/";
    private final String infoPath = "Databases/InfoDB/";

    public boolean crearDatabase(String nombreDB) {
        try {
            File carpeta = new File(dbPath);
            if (!carpeta.exists()) carpeta.mkdirs();
            File dbFile = new File(dbPath + nombreDB + ".csv");
            if (dbFile.exists()) return false;
            return dbFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean crearTabla(String dbName, String nombreTabla, List<String> atributos) {
        try {
            // Agregar estructura en archivo DB
            File dbFile = new File(dbPath + dbName + ".csv");
            if (!dbFile.exists()) return false;

            try (FileWriter writer = new FileWriter(dbFile, true)) {
                writer.write(nombreTabla + ":" + String.join(",", atributos) + "\n");
            }

            // Crear archivo de datos de la tabla
            File infoDir = new File(infoPath);
            if (!infoDir.exists()) infoDir.mkdirs();

            File tablaFile = new File(infoPath + nombreTabla + ".csv");
            if (tablaFile.exists()) return false;

            try (FileWriter writer = new FileWriter(tablaFile)) {
                writer.write(String.join(",", atributos) + "\n");
            }

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
