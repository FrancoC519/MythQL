package mythqlserver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

public class CSVDatabaseManager {
    private final String infoPath = "Databases/InfoDB/";
    private final String dbPath = "Databases/";

    public boolean crearDatabase(String nombreDB) {
        try {
            File carpeta = new File(dbPath);
            if (!carpeta.exists()) carpeta.mkdirs();
            File dbFile = new File(carpeta, nombreDB + ".csv"); // DB principal
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

            File tablaFile = new File(infoPath + dbName + "-" + nombreTabla + ".csv");
            if (tablaFile.exists()) return false;

            try (FileWriter writer = new FileWriter(tablaFile)) {
                writer.write(dbName + "\n");
                writer.write(String.join(",", atributos) + "\n");
            }

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 🔥 Eliminar una base de datos completa
    public boolean eliminarDatabase(String dbName) {
        try {
            File dbFile = new File(dbPath + dbName + ".csv");
            if (!dbFile.exists()) return false;

            // 1. Eliminar todas las tablas asociadas (archivos en InfoDB con prefijo dbName-)
            File infoDir = new File(infoPath);
            File[] tablas = infoDir.listFiles((dir, name) -> name.startsWith(dbName + "-"));
            if (tablas != null) {
                for (File tabla : tablas) {
                    tabla.delete();
                }
            }

            // 2. Eliminar archivo de la base principal
            return dbFile.delete();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 🔥 Eliminar solo una tabla
    public boolean eliminarTable(String dbName, String nombreTabla) {
        try {
            File dbFile = new File(dbPath + dbName + ".csv");
            if (!dbFile.exists()) return false;

            // 1. Borrar el archivo físico de la tabla
            File tablaFile = new File(infoPath + dbName + "-" + nombreTabla + ".csv");
            if (tablaFile.exists()) {
                tablaFile.delete();
            }

            // 2. Borrar la referencia de la tabla en el archivo de la DB
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