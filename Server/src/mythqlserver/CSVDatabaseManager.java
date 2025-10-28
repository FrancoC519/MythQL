package mythqlserver;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class CSVDatabaseManager {
    private final String dbPath = "Databases/";

    // ===== CREAR BASE DE DATOS =====
    public boolean crearDatabase(String nombreDB) {
        LockManager.bloquearBase(nombreDB);
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
        } finally {
            LockManager.desbloquearBase(nombreDB);
        }
    }

    // ===== CREAR TABLA =====
    public boolean crearTabla(String dbName, String nombreTabla, List<String> atributos) {
        LockManager.bloquearTabla(nombreTabla);
        try {
            File dbFile = new File(dbPath + dbName + ".csv");
            if (!dbFile.exists()) return false;

            List<String> lineas = Files.readAllLines(dbFile.toPath());
            List<String> nuevasLineas = new ArrayList<>();
            boolean selfBlockFound = false;

            for (String linea : lineas) {
                if (linea.trim().toUpperCase().startsWith("SELFSTACKABLES:")) {
                    selfBlockFound = true;
                    nuevasLineas.add(nombreTabla + ":" + String.join(" ", atributos));
                }
                nuevasLineas.add(linea);
            }

            if (!selfBlockFound) {
                nuevasLineas.add(nombreTabla + ":" + String.join(" ", atributos));
            }

            Files.write(dbFile.toPath(), nuevasLineas);

            File tablaFile = new File(dbPath + dbName + "_tables/" + nombreTabla + ".csv");
            if (tablaFile.exists()) return false;
            return tablaFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            LockManager.desbloquearTabla(nombreTabla);
        }
    }

    // ===== ELIMINAR DB =====
    public boolean eliminarDatabase(String dbName) {
        LockManager.bloquearBase(dbName);
        try {
            File dbFile = new File(dbPath + dbName + ".csv");
            if (!dbFile.exists()) return false;

            File tablasDir = new File(dbPath + dbName + "_tables");
            if (tablasDir.exists()) {
                File[] tablas = tablasDir.listFiles();
                if (tablas != null) {
                    for (File tabla : tablas) {
                        LockManager.bloquearTabla(tabla.getName());
                        tabla.delete();
                        LockManager.desbloquearTabla(tabla.getName());
                    }
                }
                tablasDir.delete();
            }
            return dbFile.delete();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            LockManager.desbloquearBase(dbName);
        }
    }

    // ===== ELIMINAR TABLA =====
    public boolean eliminarTable(String dbName, String nombreTabla) {
        LockManager.bloquearTabla(nombreTabla);
        try {
            File dbFile = new File(dbPath + dbName + ".csv");
            if (!dbFile.exists()) return false;

            List<String> lineas = Files.readAllLines(dbFile.toPath());
            List<String> tablaLines = new ArrayList<>();
            Map<String, Integer> counters = new HashMap<>();
            boolean selfBlockStarted = false;

            for (String linea : lineas) {
                String trimmed = linea.trim();
                if (trimmed.isEmpty()) continue;

                if (trimmed.toUpperCase().startsWith("SELFSTACKABLES:")) {
                    selfBlockStarted = true;
                    continue;
                }

                if (selfBlockStarted) {
                    if (trimmed.contains("=")) {
                        String[] partes = trimmed.split("=");
                        counters.put(partes[0].trim().toUpperCase(), Integer.parseInt(partes[1].trim()));
                    }
                } else {
                    tablaLines.add(linea);
                }
            }

            tablaLines = tablaLines.stream()
                    .filter(l -> !l.toUpperCase().startsWith(nombreTabla.toUpperCase() + ":"))
                    .collect(Collectors.toList());

            String tablaUpper = nombreTabla.toUpperCase() + ".";
            Map<String, Integer> nuevosCounters = new HashMap<>();
            for (Map.Entry<String, Integer> e : counters.entrySet()) {
                if (!e.getKey().startsWith(tablaUpper)) {
                    nuevosCounters.put(e.getKey(), e.getValue());
                }
            }

            List<String> nuevasLineas = new ArrayList<>();
            nuevasLineas.addAll(tablaLines);
            nuevasLineas.add("SELFSTACKABLES:");
            for (Map.Entry<String, Integer> e : nuevosCounters.entrySet()) {
                nuevasLineas.add(e.getKey() + " = " + e.getValue());
            }

            Files.write(dbFile.toPath(), nuevasLineas);

            File tablaFile = new File(dbPath + dbName + "_tables/" + nombreTabla + ".csv");
            if (tablaFile.exists()) tablaFile.delete();

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            LockManager.desbloquearTabla(nombreTabla);
        }
    }

    // ===== LISTADOS =====
    public List<String> listarBases() {
        File carpeta = new File(dbPath);
        if (!carpeta.exists()) return new ArrayList<>();
        return Arrays.stream(Objects.requireNonNull(carpeta.listFiles((dir, name) -> name.endsWith(".csv"))))
                .map(f -> f.getName().replace(".csv", ""))
                .collect(Collectors.toList());
    }

    public List<String> listarTablas(String dbName) {
        File dbFile = new File(dbPath + dbName + ".csv");
        if (!dbFile.exists()) return new ArrayList<>();
        try {
            return Files.readAllLines(dbFile.toPath()).stream()
                    .filter(l -> l.contains(":") && !l.startsWith("SELFSTACKABLES"))
                    .map(l -> l.split(":")[0])
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

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

    // ===== INSERTAR REGISTROS =====
    public boolean insertarRegistros(String dbName, String tableName, List<String> columnas, List<List<String>> registros) {
        LockManager.bloquearTabla(tableName);
        try {
            File dbMeta = new File(dbPath + dbName + ".csv");
            if (!dbMeta.exists()) return false;

            List<String> lineas = Files.readAllLines(dbMeta.toPath());
            Map<String, Integer> counters = new HashMap<>();
            List<String> tablaLines = new ArrayList<>();
            boolean selfBlockStarted = false;

            for (String linea : lineas) {
                String trimmed = linea.trim();
                if (trimmed.isEmpty()) continue;

                if (trimmed.toUpperCase().startsWith("SELFSTACKABLES:")) {
                    selfBlockStarted = true;
                    continue;
                }

                if (selfBlockStarted) {
                    if (trimmed.contains("=")) {
                        String[] partes = trimmed.split("=");
                        counters.put(partes[0].trim().toUpperCase(), Integer.parseInt(partes[1].trim()));
                    }
                } else {
                    tablaLines.add(linea);
                }
            }

            String definicion = null;
            for (String linea : tablaLines) {
                if (linea.toUpperCase().startsWith(tableName.toUpperCase() + ":")) {
                    definicion = linea.substring(linea.indexOf(":") + 1).trim();
                    break;
                }
            }
            if (definicion == null) return false;

            // Ahora soporta INT, VARCHAR, FLOAT, BOOL, DATE
            Pattern defPat = Pattern.compile("(\\w+)\\s+(INT|VARCHAR|FLOAT|BOOL|DATE)\\s*(\\(\\s*(\\d+)\\s*\\))?\\s*(SELF\\s*STACKABLE)?", Pattern.CASE_INSENSITIVE);
            Matcher m = defPat.matcher(definicion);

            List<String> nombres = new ArrayList<>();
            List<String> tipos = new ArrayList<>();
            List<Integer> longitudes = new ArrayList<>();
            List<Boolean> autoInc = new ArrayList<>();

            while (m.find()) {
                nombres.add(m.group(1).toUpperCase());
                tipos.add(m.group(2).toUpperCase());
                longitudes.add(m.group(4) != null ? Integer.parseInt(m.group(4)) : null);
                autoInc.add(m.group(5) != null);
            }

            // Validar los registros
            for (List<String> registro : registros) {
                if (registro.size() != columnas.size()) return false;

                for (int i = 0; i < columnas.size(); i++) {
                    String col = columnas.get(i).toUpperCase();
                    int idx = nombres.indexOf(col);
                    if (idx == -1) continue;

                    String tipo = tipos.get(idx);
                    Integer maxLen = longitudes.get(idx);
                    String valor = registro.get(i);

                    switch (tipo) {
                        case "INT":
                            if (valor.startsWith("\"") || valor.endsWith("\"") || !valor.matches("-?\\d+")) {
                                System.err.println("ERROR: Valor no entero en columna " + col);
                                return false;
                            }
                            break;

                        case "FLOAT":
                            if (valor.startsWith("\"") || valor.endsWith("\"") || !valor.matches("-?\\d+(\\.\\d+)?")) {
                                System.err.println("ERROR: Valor no flotante en columna " + col);
                                return false;
                            }
                            break;

                        case "VARCHAR":
                            if (!valor.startsWith("\"") || !valor.endsWith("\"")) {
                                System.err.println("ERROR: VARCHAR sin comillas en columna " + col);
                                return false;
                            }
                            String contenido = valor.substring(1, valor.length() - 1);
                            if (maxLen != null && contenido.length() > maxLen) {
                                System.err.println("ERROR: VARCHAR demasiado largo en columna " + col);
                                return false;
                            }
                            break;

                        case "BOOL":
                            if (!valor.equalsIgnoreCase("TRUE") && !valor.equalsIgnoreCase("FALSE")
                                    && !valor.equals("1") && !valor.equals("0")) {
                                System.err.println("ERROR: Valor no booleano en columna " + col + " (" + valor + ")");
                                return false;
                            }
                            break;

                        case "DATE":
                            // Validación formato japonés: YYYY年MM月DD日
                            if (!valor.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
                                System.err.println("ERROR: Fecha inválida (debe ser YYYY-MM-DD) en columna " + col);
                                return false;
                            }
                            break;
                    }
                }
            }

            // Escritura en archivo de tabla
            File tablaFile = new File(dbPath + dbName + "_tables/" + tableName + ".csv");
            if (!tablaFile.exists()) return false;

            try (FileWriter fw = new FileWriter(tablaFile, true)) {
                for (List<String> registro : registros) {
                    Map<String, String> valorPorColumna = new HashMap<>();
                    for (int c = 0; c < columnas.size(); c++) {
                        valorPorColumna.put(columnas.get(c).toUpperCase(), registro.get(c));
                    }

                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < nombres.size(); j++) {
                        String col = nombres.get(j);
                        String valor = valorPorColumna.get(col);

                        if (valor == null) {
                            if (autoInc.get(j)) {
                                String key = tableName.toUpperCase() + "." + col;
                                int val = counters.getOrDefault(key, 0) + 1;
                                counters.put(key, val);
                                valor = String.valueOf(val);
                            } else {
                                valor = "NULL";
                            }
                        }

                        sb.append(valor);
                        if (j < nombres.size() - 1) sb.append(",");
                    }
                    fw.write(sb.toString() + System.lineSeparator());
                }
            }

            // Actualizar contadores
            List<String> nuevasLineas = new ArrayList<>();
            for (String linea : tablaLines) {
                if (!linea.trim().isEmpty()) nuevasLineas.add(linea);
            }
            nuevasLineas.add("SELFSTACKABLES:");
            for (Map.Entry<String, Integer> e : counters.entrySet()) {
                nuevasLineas.add(e.getKey() + " = " + e.getValue());
            }

            Files.write(dbMeta.toPath(), nuevasLineas);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            LockManager.desbloquearTabla(tableName);
        }
    }

    
    // === MORPH ===
    public boolean morphTable(String dbName, String tableName, List<String> columnasCompletas) {
        LockManager.bloquearTabla(tableName);
        try {
            File dbFile = new File(dbPath + dbName + ".csv");
            if (!dbFile.exists()) return false;

            List<String> lineas = Files.readAllLines(dbFile.toPath());
            List<String> nuevasLineas = new ArrayList<>();
            boolean tablaReescrita = false;

            for (String linea : lineas) {
                String trimmed = linea.trim();

                // Mantener todo hasta SELFSTACKABLES
                if (trimmed.equalsIgnoreCase("SELFSTACKABLES:")) {
                    break;
                }

                // Detectar la tabla a reescribir
                if (trimmed.toUpperCase().startsWith(tableName.toUpperCase() + ":")) {
                    String columnasFormateadas = formatearColumnas(columnasCompletas);
                    String nuevaLinea = tableName.toUpperCase() + ":" + columnasFormateadas;
                    nuevasLineas.add(nuevaLinea);
                    tablaReescrita = true;
                } else {
                    nuevasLineas.add(linea);
                }
            }

            // Agregar bloque SELFSTACKABLES y resto
            boolean selfBlockAgregado = false;
            for (String linea : lineas) {
                if (linea.trim().equalsIgnoreCase("SELFSTACKABLES:")) {
                    nuevasLineas.add("SELFSTACKABLES:");
                    selfBlockAgregado = true;
                } else if (selfBlockAgregado) {
                    nuevasLineas.add(linea);
                }
            }

            // Si la tabla no existía antes, insertarla antes de SELFSTACKABLES
            if (!tablaReescrita) {
                String columnasFormateadas = formatearColumnas(columnasCompletas);
                int indexSelf = nuevasLineas.indexOf("SELFSTACKABLES:");
                if (indexSelf == -1) {
                    nuevasLineas.add(tableName.toUpperCase() + ":" + columnasFormateadas);
                } else {
                    nuevasLineas.add(indexSelf, tableName.toUpperCase() + ":" + columnasFormateadas);
                }
            }

            // Limpiar contenido de tabla física
            File tablaFile = new File(dbPath + dbName + "_tables/" + tableName + ".csv");
            if (tablaFile.exists()) new PrintWriter(tablaFile).close();
            else tablaFile.createNewFile();

            Files.write(dbFile.toPath(), nuevasLineas);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            LockManager.desbloquearTabla(tableName);
        }
    }

    // === Formatear columnas correctamente ===
    private String formatearColumnas(List<String> columnasCompletas) {
        StringBuilder resultado = new StringBuilder();
        int i = 0;
        while (i < columnasCompletas.size()) {
            String nombre = columnasCompletas.get(i++).toUpperCase();
            if (i >= columnasCompletas.size()) break;

            String tipo = columnasCompletas.get(i++).toUpperCase();
            String detalle = "";

            // Detectar tamaño de VARCHAR
            if ("VARCHAR".equals(tipo) && i < columnasCompletas.size() && columnasCompletas.get(i).equals("(")) {
                i++; // saltar '('
                if (i < columnasCompletas.size()) {
                    detalle = "(" + columnasCompletas.get(i++);
                    if (i < columnasCompletas.size() && columnasCompletas.get(i).equals(")")) {
                        detalle += ")";
                        i++;
                    }
                }
            }

            // Detectar atributos opcionales (ej: SELF STACKABLE)
            StringBuilder atributos = new StringBuilder();
            while (i + 1 < columnasCompletas.size() &&
                   "SELF".equals(columnasCompletas.get(i)) &&
                   "STACKABLE".equals(columnasCompletas.get(i + 1))) {
                if (atributos.length() > 0) atributos.append(" ");
                atributos.append("SELF STACKABLE");
                i += 2;
            }

            // Agregar al resultado
            if (resultado.length() > 0) resultado.append(", ");
            resultado.append(nombre).append(" ").append(tipo).append(detalle);
            if (atributos.length() > 0) resultado.append(" ").append(atributos);
        }

        return resultado.toString();
    }

    // === SWEEP ===
    public boolean vaciarTabla(String dbName, String tableName) {
        LockManager.bloquearTabla(tableName);
        try {
            File tablaFile = new File(dbPath + dbName + "_tables/" + tableName + ".csv");
            if (!tablaFile.exists()) return false;
            new PrintWriter(tablaFile).close(); // vaciar contenido
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            LockManager.desbloquearTabla(tableName);
        }
    }

    // ===== REWRITE REGISTROS =====
    public boolean rewriteRegistros(String dbName, String tableName, List<String> columnasObjetivo, List<String> nuevosValores,
                                    String columnaCond, String valorCond) {
        LockManager.bloquearTabla(tableName);
        try {
            File tablaFile = new File(dbPath + dbName + "_tables/" + tableName + ".csv");
            if (!tablaFile.exists()) return false;

            // Leer líneas
            List<String> lineas = Files.readAllLines(tablaFile.toPath());
            if (lineas.isEmpty()) return false;

            // Leer definición de columnas desde el archivo de metadatos
            File dbMeta = new File(dbPath + dbName + ".csv");
            if (!dbMeta.exists()) return false;

            String definicion = null;
            for (String linea : Files.readAllLines(dbMeta.toPath())) {
                if (linea.toUpperCase().startsWith(tableName.toUpperCase() + ":")) {
                    definicion = linea.substring(linea.indexOf(":") + 1).trim();
                    break;
                }
            }
            if (definicion == null) return false;

            // Extraer nombres de columnas
            Pattern defPat = Pattern.compile("(\\w+)\\s+(INT|VARCHAR)(?:\\s*\\(\\s*\\d+\\s*\\))?", Pattern.CASE_INSENSITIVE);
            Matcher m = defPat.matcher(definicion);
            List<String> nombresColumnas = new ArrayList<>();
            while (m.find()) {
                nombresColumnas.add(m.group(1).toUpperCase());
            }

            int idxCond = nombresColumnas.indexOf(columnaCond.toUpperCase());
            if (idxCond == -1) {
                System.err.println("Columna de condición no encontrada: " + columnaCond);
                return false;
            }

            // Limpiar comillas del valorCond si las tiene
            String valCondLimpio = valorCond.replaceAll("^\"|\"$", "");

            // Crear nuevas líneas
            List<String> nuevasLineas = new ArrayList<>();
            boolean huboCambios = false;

            for (String linea : lineas) {
                String[] valores = linea.split(",", -1);
                if (valores.length != nombresColumnas.size()) {
                    nuevasLineas.add(linea);
                    continue;
                }

                String valorActualCond = valores[idxCond].replaceAll("^\"|\"$", "");

                if (valorActualCond.equalsIgnoreCase(valCondLimpio)) {
                    // Coincidencia → actualizar columnas objetivo
                    for (int j = 0; j < columnasObjetivo.size(); j++) {
                        String col = columnasObjetivo.get(j).toUpperCase();
                        int idx = nombresColumnas.indexOf(col);
                        if (idx != -1 && j < nuevosValores.size()) {
                            String nuevo = nuevosValores.get(j);
                            if (!nuevo.startsWith("\"") && !nuevo.matches("-?\\d+")) {
                                nuevo = "\"" + nuevo.replace("\"", "") + "\"";
                            }
                            valores[idx] = nuevo;
                        }
                    }
                    huboCambios = true;
                }

                nuevasLineas.add(String.join(",", valores));
            }

            if (huboCambios) {
                Files.write(tablaFile.toPath(), nuevasLineas);
            }

            return huboCambios;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            LockManager.desbloquearTabla(tableName);
        }
    }
}
