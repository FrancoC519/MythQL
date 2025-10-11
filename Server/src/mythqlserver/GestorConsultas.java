package mythqlserver;
import java.io.File;
import java.util.*;
import java.util.regex.*;
import java.util.function.Consumer;

public class GestorConsultas {
    private final String dbPath = "Databases/";
    private Consumer<String> messageCallback;

    public GestorConsultas() { this.messageCallback = null; }
    public GestorConsultas(Consumer<String> callback) { this.messageCallback = callback; }

    public String procesarConsulta(String consulta, User user) {
        List<String> tokens = tokenizar(consulta);
        if (tokens.isEmpty()) return "ERROR: Consulta vacía.";
        String comando = tokens.get(0).toUpperCase();
        enviarMensaje("Comando recibido: " + comando);
        switch (comando) {
            case "SUMMON":   return comandoSummon(tokens, user);
            case "UTILIZE":  return comandoUtilize(tokens, user);
            case "BRING":    return comandoBring(tokens, user);
            case "BURN":     return comandoBurn(tokens, user);
            case "MANIFEST": return comandoManifest(tokens, user);
            case "DEPICT":   return comandoDepict(tokens, user);
            case "FILE":     return comandoFile(tokens, user);
            default:         return "ERROR: Comando desconocido '" + comando + "'";
        }
    }

    private List<String> tokenizar(String consulta) {
        Pattern pattern = Pattern.compile("\"[^\"]*\"|[A-Za-z0-9_]+|[{}(),\\[\\]]");
        Matcher matcher = pattern.matcher(consulta);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            String tok = matcher.group();
            // solo mayúsculas en identificadores, no en strings
            if (!tok.startsWith("\"")) tok = tok.toUpperCase();
            tokens.add(tok);
        }
        return tokens;
    }


    // === MANIFEST ===
    private String comandoManifest(List<String> tokens, User user) {
        CSVDatabaseManager db = new CSVDatabaseManager();
        if (tokens.size() != 2) return "ERROR: Uso: MANIFEST DATABASES | MANIFEST TABLES";
        if ("DATABASES".equals(tokens.get(1))) {
            List<String> bases = db.listarBases();
            return bases.isEmpty() ? "No hay bases de datos." : "Bases: " + String.join(", ", bases);
        } else if ("TABLES".equals(tokens.get(1))) {
            if (user.getBaseActiva() == null) return "ERROR: No hay base activa.";
            List<String> tablas = db.listarTablas(user.getBaseActiva());
            return tablas.isEmpty() ? "No hay tablas en DB " + user.getBaseActiva()
                                    : "Tablas en " + user.getBaseActiva() + ": " + String.join(", ", tablas);
        }
        return "ERROR: MANIFEST debe ser DATABASES o TABLES";
    }

    // === DEPICT ===
    private String comandoDepict(List<String> tokens, User user) {
        if (tokens.size() != 2) return "ERROR: Uso: DEPICT <tabla>";
        if (user.getBaseActiva() == null) return "ERROR: No hay base activa.";
        CSVDatabaseManager db = new CSVDatabaseManager();
        return db.describirTabla(user.getBaseActiva(), tokens.get(1));
    }

    private String comandoUtilize(List<String> tokens, User user) {
        if (tokens.size() != 2) {
            return "ERROR: Sintaxis UTILIZE inválida. Uso: UTILIZE <nombreDB>";
        }
        String base = tokens.get(1);
        File dbFile = new File(dbPath + base + ".csv");
        if (!dbFile.exists()) {
            return "ERROR: La base de datos '" + base + "' no existe.";
        }
        user.setBaseActiva(base);
        enviarMensaje("Base activa cambiada a: " + base);
        return "OK: Base de datos activa = " + base;
    }

    private String comandoSummon(List<String> tokens, User user) {
        String msg;
        if (tokens.size() < 3) {
            msg = "ERROR: Sintaxis SUMMON inválida.";
            return msg;
        }
        CSVDatabaseManager db = new CSVDatabaseManager();
        if ("DATABASE".equals(tokens.get(1))) {
            String nombreDB = tokens.get(2);
            boolean exito = db.crearDatabase(nombreDB);
            msg = exito ? "OK: Base de datos '" + nombreDB + "' creada."
                        : "ERROR: No se pudo crear la base de datos.";
            enviarMensaje(msg);
            return msg;
        }
        if ("TABLE".equals(tokens.get(1))) {
            if (user.getBaseActiva() == null) {
                return "ERROR: No hay base activa. Use UTILIZE <db> primero.";
            }
            File dbFile = new File(dbPath + user.getBaseActiva() + ".csv");
            if (!dbFile.exists()) {
                return "ERROR: La base activa '" + user.getBaseActiva() + "' no existe.";
            }
            String nombreTabla = tokens.get(2);
            List<String> atributos = new ArrayList<>();
            for (int i = 4; i < tokens.size(); i++) {
                if ("}".equals(tokens.get(i))) break;
                atributos.add(tokens.get(i));
            }
            boolean exito = db.crearTabla(user.getBaseActiva(), nombreTabla, atributos);
            msg = exito ? "OK: Tabla '" + nombreTabla + "' creada en DB " + user.getBaseActiva()
                        : "ERROR: No se pudo crear la tabla.";
            enviarMensaje(msg);
            return msg;
        }
        msg = "ERROR: SUMMON debe ser DATABASE o TABLE.";
        enviarMensaje(msg);
        return msg;
    }

    private String comandoBring(List<String> tokens, User user) {
        if (tokens.size() < 2)
            return "ERROR: Sintaxis BRING inválida. Uso: BRING <tabla> [ { columnas } ]";
        if (user.getBaseActiva() == null)
            return "ERROR: No hay base activa.";

        String dbName = user.getBaseActiva();
        String tableName = tokens.get(1);
        File tablaFile = new File(dbPath + dbName + "_tables/" + tableName + ".csv");
        if (!tablaFile.exists())
            return "ERROR: Tabla '" + tableName + "' no encontrada.";

        try {
            // === Leer definición desde el archivo de la DB ===
            File dbFile = new File(dbPath + dbName + ".csv");
            if (!dbFile.exists())
                return "ERROR: Archivo de base '" + dbName + "' no encontrado.";

            List<String> dbLines = java.nio.file.Files.readAllLines(dbFile.toPath());
            String definicion = null;
            for (String linea : dbLines) {
                if (linea.toUpperCase().startsWith(tableName.toUpperCase() + ":")) {
                    definicion = linea.substring(linea.indexOf(":") + 1).trim();
                    break;
                }
            }
            if (definicion == null)
                return "ERROR: Definición de tabla '" + tableName + "' no encontrada.";

            // === Extraer nombres de columnas de la definición ===
            Pattern defPat = Pattern.compile("(\\w+)\\s+(INT|VARCHAR)(?:\\s*\\(\\s*\\d+\\s*\\))?", Pattern.CASE_INSENSITIVE);
            Matcher m = defPat.matcher(definicion);
            List<String> nombresColumnas = new ArrayList<>();
            while (m.find()) {
                nombresColumnas.add(m.group(1).toUpperCase());
            }
            if (nombresColumnas.isEmpty())
                return "ERROR: No se pudieron extraer las columnas de la definición.";

            // === Si se especifican columnas dentro de { } ===
            List<String> columnasFiltradas = new ArrayList<>();
            if (tokens.size() > 2 && "{".equals(tokens.get(2))) {
                int i = 3;
                while (i < tokens.size() && !"}".equals(tokens.get(i))) {
                    String col = tokens.get(i++);
                    columnasFiltradas.add(col.toUpperCase());
                    if (i < tokens.size() && ",".equals(tokens.get(i))) i++;
                }
            }

            boolean hayFiltro = !columnasFiltradas.isEmpty();
            List<Integer> indices = new ArrayList<>();

            if (hayFiltro) {
                for (String c : columnasFiltradas) {
                    int idx = nombresColumnas.indexOf(c);
                    if (idx != -1) indices.add(idx);
                }
                if (indices.isEmpty())
                    return "ERROR: Ninguna de las columnas especificadas existe en la tabla.";
            } else {
                for (int i = 0; i < nombresColumnas.size(); i++)
                    indices.add(i);
            }

            // === Leer los registros del archivo de la tabla ===
            List<String> lineas = java.nio.file.Files.readAllLines(tablaFile.toPath());
            if (lineas.isEmpty()) {
                enviarMensaje("(tabla vacía)");
                return "(tabla vacía)";
            }

            // === Construir salida: encabezado + registros ===
            StringBuilder sb = new StringBuilder();

            // Encabezado de tabla
            String header1 = "Tabla: " + tableName.toUpperCase() + " ";
            String header2 = String.join(" | ", indices.stream().map(nombresColumnas::get).toList());
            
            
            enviarMensaje(header1);
            enviarMensaje(header2);
            enviarMensaje("||");

            sb.append(header1).append("");
            sb.append(header2).append("");
            sb.append("||");

            // Registros
            for (String linea : lineas) {
                String[] valores = linea.split(",", -1);
                List<String> seleccionados = new ArrayList<>();
                for (int idx : indices) {
                    if (idx < valores.length)
                        seleccionados.add(valores[idx]);
                    else
                        seleccionados.add("NULL");
                }
                String fila = String.join(" | ", seleccionados);
                enviarMensaje(fila);
                sb.append(fila).append("\\");
            }
            
            return sb.toString();

        } catch (Exception e) {
            e.printStackTrace();
            enviarMensaje("ERROR: No se pudo leer la tabla.");
            return "ERROR: No se pudo leer la tabla '" + tableName + "'.";
        }
    }
    
    private String comandoBurn(List<String> tokens, User user) {
        if (tokens.size() < 3) {
            String msg = "ERROR: Sintaxis BURN inválida.";
            enviarMensaje(msg);
            return msg;
        }
        CSVDatabaseManager db = new CSVDatabaseManager();
        // BURN DATABASE <db>
        if ("DATABASE".equals(tokens.get(1))) {
            String dbName = tokens.get(2);
            boolean exito = db.eliminarDatabase(dbName);
            String msg = exito ? "OK: Base de datos '" + dbName + "' eliminada."
                               : "ERROR: No se pudo eliminar la base de datos.";
            enviarMensaje(msg);
            return msg;
        }
        // BURN TABLE <table>
        if ("TABLE".equals(tokens.get(1))) {
            if (user.getBaseActiva() == null) {
                String msg = "ERROR: No hay base activa.";
                enviarMensaje(msg);
                return msg;
            }
            String nombreTabla = tokens.get(2);
            boolean exito = db.eliminarTable(user.getBaseActiva(), nombreTabla);
            String msg = exito ? "OK: Tabla '" + nombreTabla + "' eliminada de DB '" + user.getBaseActiva() + "'."
                               : "ERROR: No se pudo eliminar la tabla.";
            enviarMensaje(msg);
            return msg;
        }
        return "ERROR: BURN debe ser DATABASE o TABLE.";
    }
    
    private String comandoFile(List<String> tokens, User user) {
        if (user.getBaseActiva() == null)
            return "ERROR: No hay base activa. Use UTILIZE <db> primero.";

        if (tokens.size() < 4)
            return "ERROR: Sintaxis FILE incompleta.";

        int i = 1;
        String tabla = tokens.get(i++);
        if (!new File("Databases/" + user.getBaseActiva() + "_tables/" + tabla + ".csv").exists())
            return "ERROR: La tabla '" + tabla + "' no existe en la base activa.";

        if (!"{".equals(tokens.get(i++)))
            return "ERROR: Falta '{' tras el nombre de tabla.";

        List<String> columnas = new ArrayList<>();
        while (i < tokens.size() && !"}".equals(tokens.get(i))) {
            columnas.add(tokens.get(i++));
            if (",".equals(tokens.get(i))) i++;
        }
        if (i >= tokens.size() || !"}".equals(tokens.get(i++)))
            return "ERROR: Falta '}' en lista de columnas.";

        List<List<String>> registros = new ArrayList<>();

        while (i < tokens.size()) {
            if (!"[".equals(tokens.get(i++))) return "ERROR: Falta '[' en registro.";
            List<String> valores = new ArrayList<>();
            while (i < tokens.size() && !"]".equals(tokens.get(i))) {
                valores.add(tokens.get(i++));
                if (",".equals(tokens.get(i))) i++;
            }
            if (i >= tokens.size() || !"]".equals(tokens.get(i++)))
                return "ERROR: Falta ']' de cierre.";
            registros.add(valores);
            if (i < tokens.size() && ",".equals(tokens.get(i))) i++;
        }

        CSVDatabaseManager db = new CSVDatabaseManager();
        boolean ok = db.insertarRegistros(user.getBaseActiva(), tabla, columnas, registros);
        return ok ? "OK: Registros insertados en " + tabla
                  : "ERROR: Fallo al insertar registros en " + tabla;
    }


    private void enviarMensaje(String msg) {
        if (messageCallback != null) {
            messageCallback.accept(msg);
        } else {
            System.out.println(msg);
        }
    }
}
