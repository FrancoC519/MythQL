package mythqlserver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

public class CSVDatabaseManager {
    private final String dbPath = "Databases/";

    public boolean crearDatabase(String nombreDB) {
        try {
            File carpeta = new File(dbPath);
            if (!carpeta.exists()) carpeta.mkdirs();

            File dbFile = new File(carpeta, nombreDB + ".csv");
            if (dbFile.exists()) return false;

            // Crear archivo principal de la DB
            if (!dbFile.createNewFile()) return false;

            // Crear carpeta de tablas para la DB
            File tablasDir = new File(dbPath + nombreDB + "_tables");
            if (!tablasDir.exists()) tablasDir.mkdirs();

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean crearTabla(String dbName, String nombreTabla, List<String> atributos) {
        try {
            File dbFile = new File(dbPath + dbName + ".csv");
            if (!dbFile.exists()) return false;

            // Guardar metadatos en el archivo de la DB
            try (FileWriter writer = new FileWriter(dbFile, true)) {
                String linea = nombreTabla + ":" + String.join(" ", atributos);
                writer.write(linea + System.lineSeparator());
            }

            // Crear archivo vacío para la tabla en la carpeta de la DB
            File tablaFile = new File(dbPath + dbName + "_tables/" + nombreTabla + ".csv");
            if (tablaFile.exists()) return false;
            return tablaFile.createNewFile();

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean eliminarDatabase(String dbName) {
        try {
            File dbFile = new File(dbPath + dbName + ".csv");
            if (!dbFile.exists()) return false;

            // Eliminar carpeta de tablas asociada
            File tablasDir = new File(dbPath + dbName + "_tables");
            if (tablasDir.exists()) {
                File[] tablas = tablasDir.listFiles();
                if (tablas != null) {
                    for (File tabla : tablas) {
                        tabla.delete();
                    }
                }
                tablasDir.delete();
            }

            // Eliminar archivo principal de la DB
            return dbFile.delete();

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean eliminarTable(String dbName, String nombreTabla) {
        try {
            File dbFile = new File(dbPath + dbName + ".csv");
            if (!dbFile.exists()) return false;

            // Eliminar archivo físico de la tabla
            File tablaFile = new File(dbPath + dbName + "_tables/" + nombreTabla + ".csv");
            if (tablaFile.exists()) tablaFile.delete();

            // Borrar la referencia de la tabla en el archivo de la DB
            List<String> lineas = Files.readAllLines(dbFile.toPath());
            List<String> nuevasLineas = lineas.stream()
                    .filter(l -> !l.startsWith(nombreTabla + ":"))
                    .collect(Collectors.toList());
            Files.write(dbFile.toPath(), nuevasLineas);

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
