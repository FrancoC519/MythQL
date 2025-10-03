package mythqlserver;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class CSVDatabaseManager {
    private final String dbPath = "Databases/";

    public boolean crearDatabase(String nombreDB) {
        try {
            File carpeta = new File(dbPath);
            if (!carpeta.exists()) carpeta.mkdirs();
            File dbFile = new File(carpeta, nombreDB + ".csv");
            if (dbFile.exists()) return false;
            if (!dbFile.createNewFile()) return false;
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
            try (FileWriter writer = new FileWriter(dbFile, true)) {
                String linea = nombreTabla + ":" + String.join(" ", atributos);
                writer.write(linea + System.lineSeparator());
            }
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
            File tablasDir = new File(dbPath + dbName + "_tables");
            if (tablasDir.exists()) {
                File[] tablas = tablasDir.listFiles();
                if (tablas != null) for (File tabla : tablas) tabla.delete();
                tablasDir.delete();
            }
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
            File tablaFile = new File(dbPath + dbName + "_tables/" + nombreTabla + ".csv");
            if (tablaFile.exists()) tablaFile.delete();
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

    // ======= NUEVO: listar bases =======
    public List<String> listarBases() {
        File carpeta = new File(dbPath);
        if (!carpeta.exists()) return new ArrayList<>();
        return Arrays.stream(Objects.requireNonNull(carpeta.listFiles((dir, name) -> name.endsWith(".csv"))))
                .map(f -> f.getName().replace(".csv", ""))
                .collect(Collectors.toList());
    }

    // ======= NUEVO: listar tablas =======
    public List<String> listarTablas(String dbName) {
        File dbFile = new File(dbPath + dbName + ".csv");
        if (!dbFile.exists()) return new ArrayList<>();
        try {
            return Files.readAllLines(dbFile.toPath()).stream()
                    .map(l -> l.split(":")[0])
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    // ======= NUEVO: describir tabla =======
    public String describirTabla(String dbName, String tabla) {
        File dbFile = new File(dbPath + dbName + ".csv");
        if (!dbFile.exists()) return "ERROR: Base no existe";
        try {
            for (String linea : Files.readAllLines(dbFile.toPath())) {
                if (linea.startsWith(tabla + ":")) {
                    return "Tabla " + tabla + " -> " + linea.substring(linea.indexOf(":") + 1);
                }
            }
            return "ERROR: Tabla '" + tabla + "' no encontrada en DB '" + dbName + "'";
        } catch (IOException e) {
            return "ERROR: No se pudo leer DB.";
        }
    }
}
