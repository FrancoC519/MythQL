package mythqlserver;
import java.io.File;
import java.util.*;
import java.util.regex.*;
import java.util.function.Consumer;

public class GestorConsultas {
    private final String dbPath = "Databases/";
    private Consumer<String> messageCallback;
    private Consumer<String> notificacionCallback;

    public GestorConsultas() { 
        this.messageCallback = null; 
        this.notificacionCallback = null;
    }
    
    public GestorConsultas(Consumer<String> callback) { 
        this.messageCallback = callback; 
        this.notificacionCallback = null;
    }
    
    public GestorConsultas(Consumer<String> msgCallback, Consumer<String> notifCallback) { 
        this.messageCallback = msgCallback;
        this.notificacionCallback = notifCallback;
    }

    public String procesarConsulta(String consulta, User user) {
        List<String> tokens = tokenizar(consulta);
        if (tokens.isEmpty()) return "ERROR: Consulta vacía.";
        String comando = tokens.get(0).toUpperCase();
        enviarMensaje("Comando recibido: " + comando);
        switch (comando) {
            case "SUMMON": return comandoSummon(tokens, user);
            case "UTILIZE": return comandoUtilize(tokens, user);
            case "BRING": return comandoBring(tokens, user);
            case "BURN": return comandoBurn(tokens, user);
            case "FILE": return comandoFile(tokens, user);
            case "MANIFEST": return comandoManifest(tokens, user);
            case "DEPICT": return comandoDepict(tokens, user);
            case "MORPH": return comandoMorph(tokens, user);
            case "SWEEP": return comandoSweep(tokens, user);
            case "REWRITE": return comandoRewrite(tokens, user);
            default: return "ERROR: Comando desconocido '" + comando + "'";
        }
    }

    // Método para obtener esquemas jerárquicos
    public String obtenerEsquemasJerarquicos() {
        try {
            enviarMensaje("Solicitando esquemas jerárquicos...");
            CSVDatabaseManager db = new CSVDatabaseManager();
            List<String> bases = db.listarBases();
            Map<String, List<String>> esquemas = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            
            for (String base : bases) {
                List<String> tablas = db.listarTablas(base);
                esquemas.put(base, tablas);
            }
            
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, List<String>> entry : esquemas.entrySet()) {
                if (sb.length() > 0) sb.append(";");
                sb.append(entry.getKey()).append("{");
                sb.append(String.join(",", entry.getValue()));
                sb.append("}");
            }
            
            String resultado = sb.toString();
            enviarMensaje("Esquemas obtenidos: " + (bases.size() + " bases, " + 
                esquemas.values().stream().mapToInt(List::size).sum() + " tablas totales"));
            return resultado;
        } catch (Exception e) {
            enviarMensaje("Error obteniendo esquemas: " + e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    // ========== NUEVO TOKENIZADOR NUEVO ==========
    private List<String> tokenizar(String consulta) {
        List<String> tokens = new ArrayList<>();

        Pattern pattern = Pattern.compile(
            // 1️⃣ Fechas tipo YYYY-MM-DD
            "\\d{4}-\\d{2}-\\d{2}" +
            // 2️⃣ O strings entre comillas simples o dobles
            "|'[^']*'|\"[^\"]*\"" +
            // 3️⃣ Números con o sin decimales
            "|\\d+\\.\\d+|\\d+" +
            // 4️⃣ Palabras (identificadores)
            "|[A-Za-z_][A-Za-z0-9_]*" +
            // 5️⃣ Símbolos estructurales
            "|[{}\\[\\](),;]"
        );

        Matcher matcher = pattern.matcher(consulta);

        while (matcher.find()) {
            String token = matcher.group();

            // Mantener literales y números como están, pero pasar keywords a upper
            if (token.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                tokens.add(token.toUpperCase());
            } else {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String comandoManifest(List<String> tokens, User user) {
        CSVDatabaseManager db = new CSVDatabaseManager();
        if (tokens.size() != 2) return "ERROR: Uso: MANIFEST DATABASES | MANIFEST TABLES";
        if ("DATABASES".equals(tokens.get(1))) {
            List<String> bases = db.listarBases();
            enviarMensaje("MANIFEST DATABASES: " + bases.size() + " bases encontradas");
            return bases.isEmpty() ? "No hay bases de datos." : "Bases: " + String.join(", ", bases);
        } else if ("TABLES".equals(tokens.get(1))) {
            if (user.getBaseActiva() == null) return "ERROR: No hay base activa.";
            List<String> tablas = db.listarTablas(user.getBaseActiva());
            enviarMensaje("MANIFEST TABLES en " + user.getBaseActiva() + ": " + tablas.size() + " tablas encontradas");
            return tablas.isEmpty() ? "No hay tablas en DB " + user.getBaseActiva()
                                    : "Tablas en " + user.getBaseActiva() + ": " + String.join(", ", tablas);
        }
        return "ERROR: MANIFEST debe ser DATABASES o TABLES";
    }

    private String comandoDepict(List<String> tokens, User user) {
        if (tokens.size() != 2) return "ERROR: Uso: DEPICT <tabla>";
        if (user.getBaseActiva() == null) return "ERROR: No hay base activa.";
        CSVDatabaseManager db = new CSVDatabaseManager();
        String tabla = tokens.get(1);
        enviarMensaje("DEPICT tabla: " + tabla + " en base: " + user.getBaseActiva());
        String resultado = db.describirTabla(user.getBaseActiva(), tabla);
        enviarMensaje("Descripción: " + resultado);
        return resultado;
    }

    private String comandoUtilize(List<String> tokens, User user) {
        if (tokens.size() != 2) {
            return "ERROR: Sintaxis UTILIZE inválida. Uso: UTILIZE <nombreDB>";
        }
        String base = tokens.get(1);
        File dbFile = new File(dbPath + base + ".csv");
        if (!dbFile.exists()) {
            enviarMensaje("UTILIZE falló: Base '" + base + "' no existe");
            return "ERROR: La base de datos '" + base + "' no existe.";
        }
        user.setBaseActiva(base);
        enviarMensaje("Base activa cambiada a: " + base + " para usuario: " + user.getUsername());
        
        if (notificacionCallback != null) {
            notificacionCallback.accept("Usuario " + user.getUsername() + " cambió base activa a: " + base);
        }
        
        return "OK: Base de datos activa = " + base;
    }

    private String comandoSummon(List<String> tokens, User user) {
        String msg;
        if (tokens.size() < 3) {
            msg = "ERROR: Sintaxis SUMMON inválida.";
            enviarMensaje(msg);
            return msg;
        }
        CSVDatabaseManager db = new CSVDatabaseManager();
        if ("DATABASE".equals(tokens.get(1))) {
            String nombreDB = tokens.get(2);
            enviarMensaje(" SUMMON DATABASE: " + nombreDB + " por usuario: " + user.getUsername());
            boolean exito = db.crearDatabase(nombreDB);
            msg = exito ? "OK: Base de datos '" + nombreDB + "' creada."
                        : "ERROR: No se pudo crear la base de datos.";
            enviarMensaje(exito ? msg : msg);
            
            if (exito && notificacionCallback != null) {
                notificacionCallback.accept("Base de datos '" + nombreDB + "' creada por " + user.getUsername());
            }
            
            return msg;
        }
        if ("TABLE".equals(tokens.get(1))) {
            if (user.getBaseActiva() == null) {
                msg = "ERROR: No hay base activa. Use UTILIZE <db> primero.";
                enviarMensaje(msg);
                return msg;
            }
            File dbFile = new File(dbPath + user.getBaseActiva() + ".csv");
            if (!dbFile.exists()) {
                msg = "ERROR: La base activa '" + user.getBaseActiva() + "' no existe.";
                enviarMensaje(msg);
                return msg;
            }
            String nombreTabla = tokens.get(2);
            List<String> atributos = new ArrayList<>();
            for (int i = 4; i < tokens.size(); i++) {
                if ("}".equals(tokens.get(i))) break;
                atributos.add(tokens.get(i));
            }
            enviarMensaje("SUMMON TABLE: " + nombreTabla + " en " + user.getBaseActiva() + 
                         " con atributos: " + atributos + " por usuario: " + user.getUsername());
            boolean exito = db.crearTabla(user.getBaseActiva(), nombreTabla, atributos);
            msg = exito ? "OK: Tabla '" + nombreTabla + "' creada en DB " + user.getBaseActiva()
                        : "ERROR: No se pudo crear la tabla.";
            enviarMensaje(exito ? msg : msg);
            
            if (exito && notificacionCallback != null) {
                notificacionCallback.accept("Tabla '" + nombreTabla + "' creada en " + 
                                           user.getBaseActiva() + " por " + user.getUsername());
            }
            
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
            File dbFile = new File(dbPath + dbName + ".csv");
            if (!dbFile.exists())
                return "ERROR: Archivo de base '" + dbName + "' no encontrado.";

            // === Leer definición de tabla ===
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

            // === Extraer nombres de columnas ===
            Pattern defPat = Pattern.compile("(\\w+)\\s+(INT|VARCHAR|DATE|BOOL|FLOAT)(?:\\s*\\(\\s*\\d+\\s*\\))?", Pattern.CASE_INSENSITIVE);
            Matcher m = defPat.matcher(definicion);
            List<String> nombresColumnas = new ArrayList<>();
            while (m.find()) {
                nombresColumnas.add(m.group(1).toUpperCase());
            }
            if (nombresColumnas.isEmpty())
                return "ERROR: No se pudieron extraer las columnas de la definición.";

            // === Leer registros ===
            List<String> lineas = java.nio.file.Files.readAllLines(tablaFile.toPath());
            if (lineas.isEmpty())
                return "(tabla vacía)";

            // === Construir salida ===
            StringBuilder sb = new StringBuilder();

            // Encabezado
            String header = tableName.toUpperCase() + " " + String.join(" \\ ", nombresColumnas);
            sb.append(header).append("||");

            // Registros
            for (String linea : lineas) {
                String[] valores = linea.split(",", -1);
                String fila = String.join(" | ", valores);
                sb.append(fila).append("\\");
            }

            return sb.toString();

        } catch (Exception e) {
            e.printStackTrace();
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
        if ("DATABASE".equals(tokens.get(1))) {
            String dbName = tokens.get(2);
            enviarMensaje("BURN DATABASE: " + dbName + " por usuario: " + user.getUsername());
            boolean exito = db.eliminarDatabase(dbName);
            String msg = exito ? "OK: Base de datos '" + dbName + "' eliminada."
                               : "ERROR: No se pudo eliminar la base de datos.";
            enviarMensaje(exito ? msg : msg);
            
            if (exito && notificacionCallback != null) {
                notificacionCallback.accept("Base de datos '" + dbName + "' eliminada por " + user.getUsername());
            }
            
            return msg;
        }
        if ("TABLE".equals(tokens.get(1))) {
            if (user.getBaseActiva() == null) {
                String msg = "ERROR: No hay base activa.";
                enviarMensaje(msg);
                return msg;
            }
            String nombreTabla = tokens.get(2);
            enviarMensaje("BURN TABLE: " + nombreTabla + " de " + user.getBaseActiva() + " por usuario: " + user.getUsername());
            boolean exito = db.eliminarTable(user.getBaseActiva(), nombreTabla);
            String msg = exito ? "OK: Tabla '" + nombreTabla + "' eliminada de DB '" + user.getBaseActiva() + "'."
                               : "ERROR: No se pudo eliminar la tabla.";
            enviarMensaje(exito ? msg : msg);
            
            if (exito && notificacionCallback != null) {
                notificacionCallback.accept("Tabla '" + nombreTabla + "' eliminada de " + 
                                           user.getBaseActiva() + " por " + user.getUsername());
            }
            
            return msg;
        }
        return "ERROR: BURN debe ser DATABASE o TABLE.";
    }
    
    private String comandoFile(List<String> tokens, User user) {
        try {
            if (user.getBaseActiva() == null)
                return "ERROR: No hay base activa. Use UTILIZE <db> primero.";

            if (tokens.size() < 4)
                return "ERROR: Sintaxis FILE incompleta.";

            int i = 1;
            String tabla = tokens.get(i++);

            File tablaFile = new File("Databases/" + user.getBaseActiva() + "_tables/" + tabla + ".csv");
            if (!tablaFile.exists())
                return "ERROR: La tabla '" + tabla + "' no existe en la base activa.";

            if (i >= tokens.size() || !"{".equals(tokens.get(i++)))
                return "ERROR: Falta '{' tras el nombre de la tabla.";

            // === Leer columnas ===
            List<String> columnas = new ArrayList<>();
            while (i < tokens.size() && !"}".equals(tokens.get(i))) {
                String col = tokens.get(i++);
                if (!col.matches("[A-Za-z_][A-Za-z0-9_]*"))
                    return "ERROR: Nombre de columna inválido: " + col;
                columnas.add(col);
                if (i < tokens.size() && ",".equals(tokens.get(i))) i++;
            }

            if (i >= tokens.size() || !"}".equals(tokens.get(i++)))
                return "ERROR: Falta '}' al final de la lista de columnas.";

            // === Leer registros ===
            List<List<String>> registros = new ArrayList<>();

            while (i < tokens.size()) {
                if (!"[".equals(tokens.get(i++)))
                    return "ERROR: Falta '[' antes de un registro.";

                List<String> valores = new ArrayList<>();
                while (i < tokens.size() && !"]".equals(tokens.get(i))) {
                    String val = tokens.get(i++);

                    // Verificar formato de valor
                    if (!esValorValido(val))
                        return "ERROR: Valor inválido: " + val;

                    valores.add(val);
                    if (i < tokens.size() && ",".equals(tokens.get(i))) i++;
                }

                if (i >= tokens.size() || !"]".equals(tokens.get(i++)))
                    return "ERROR: Falta ']' de cierre en un registro.";

                if (valores.size() != columnas.size())
                    return "ERROR: El registro tiene " + valores.size() + " valores, pero se esperaban " + columnas.size() + ".";

                registros.add(valores);

                // Saltar separadores opcionales
                if (i < tokens.size() && (",".equals(tokens.get(i)) || ";".equals(tokens.get(i)))) i++;
            }

            if (registros.isEmpty())
                return "ERROR: No se encontraron registros válidos.";

            // === Insertar en CSV ===
            enviarMensaje("FILE insertando " + registros.size() + " registros en '" + tabla + "' (" + user.getBaseActiva() + ")");
            CSVDatabaseManager db = new CSVDatabaseManager();
            boolean ok = db.insertarRegistros(user.getBaseActiva(), tabla, columnas, registros);

            if (ok && notificacionCallback != null) {
                notificacionCallback.accept("Registros insertados en tabla '" + tabla + "' de " + user.getBaseActiva() + " por " + user.getUsername());
            }

            String resultado = ok
                    ? "OK: Registros insertados correctamente en " + tabla
                    : "ERROR: Fallo al insertar registros en " + tabla;

            enviarMensaje(resultado);
            return resultado;

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: Excepción en comando FILE → " + e.getMessage();
        }
    }

    private boolean esValorValido(String val) {
        // NULL
        if (val.equalsIgnoreCase("NULL")) return true;

        // INT o FLOAT
        if (val.matches("-?\\d+(\\.\\d+)?")) return true;

        // BOOL
        if (val.equalsIgnoreCase("TRUE") || val.equalsIgnoreCase("FALSE") || val.equals("1") || val.equals("0"))
            return true;

        // DATE (YYYY-MM-DD o japonés)
        if (val.matches("\\d{4}-\\d{2}-\\d{2}")) return true;
        if (val.matches("\\d{4}年\\d{1,2}月\\d{1,2}日")) return true;

        // STRING (comillas dobles o simples)
        if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'")))
            return true;

        return false;
    }

    
    // ===== COMANDO MORPH =====
    private String comandoMorph(List<String> tokens, User user) {
        if (user.getBaseActiva() == null)
            return "ERROR: No hay base activa.";
        if (tokens.size() < 4)
            return "ERROR: Sintaxis MORPH inválida. Uso: MORPH <tabla>{columna tipo,...}";

        String tabla = tokens.get(1);
        List<String> atributos = new ArrayList<>();
        int i = 3;
        while (i < tokens.size() && !"}".equals(tokens.get(i))) {
            atributos.add(tokens.get(i++));
            if (",".equals(tokens.get(i))) i++;
        }

        CSVDatabaseManager db = new CSVDatabaseManager();
        boolean ok = db.morphTable(user.getBaseActiva(), tabla, atributos);
        enviarMensaje("MORPH tabla " + tabla + " => " + atributos);
        return ok ? "OK: Tabla '" + tabla + "' modificada."
                  : "ERROR: Fallo al modificar tabla '" + tabla + "'.";
    }

    // ===== COMANDO SWEEP =====
    private String comandoSweep(List<String> tokens, User user) {
        if (user.getBaseActiva() == null)
            return "ERROR: No hay base activa.";
        if (tokens.size() != 2)
            return "ERROR: Uso: SWEEP <tabla>";

        String tabla = tokens.get(1);
        CSVDatabaseManager db = new CSVDatabaseManager();
        boolean ok = db.vaciarTabla(user.getBaseActiva(), tabla);
        enviarMensaje("SWEEP tabla " + tabla + " en " + user.getBaseActiva());
        return ok ? "OK: Tabla '" + tabla + "' limpiada."
                  : "ERROR: No se pudo limpiar tabla '" + tabla + "'.";
    }

    // ===== COMANDO REWRITE =====
    private String comandoRewrite(List<String> tokens, User user) {
        if (user.getBaseActiva() == null)
            return "ERROR: No hay base activa.";

        try {
            String tabla = tokens.get(1);
            int i = 2;

            if (!"{".equals(tokens.get(i++)))
                return "ERROR: Falta '{' tras el nombre de tabla.";

            List<String> columnas = new ArrayList<>();
            while (i < tokens.size() && !"}".equals(tokens.get(i))) {
                columnas.add(tokens.get(i++));
                if (",".equals(tokens.get(i))) i++;
            }
            i++; // cerrar }

            if (!"[".equals(tokens.get(i++)))
                return "ERROR: Falta '[' tras columnas.";

            List<String> valores = new ArrayList<>();
            while (i < tokens.size() && !"]".equals(tokens.get(i))) {
                valores.add(tokens.get(i++));
                if (",".equals(tokens.get(i))) i++;
            }
            i++; // cerrar ]

            if (i >= tokens.size() || !"IN".equals(tokens.get(i++)) || !"WHICH".equals(tokens.get(i++)))
                return "ERROR: Falta cláusula IN WHICH.";

            if (!"{".equals(tokens.get(i++)))
                return "ERROR: Falta '{' en condición.";

            String colCond = tokens.get(i++);
            if (!"}".equals(tokens.get(i++)))
                return "ERROR: Falta '}' tras condición.";

            if (!"EQUALS".equals(tokens.get(i++)))
                return "ERROR: Falta palabra clave EQUALS.";

            if (!"[".equals(tokens.get(i++)))
                return "ERROR: Falta '[' en valor condición.";

            String valCond = tokens.get(i++);
            if (!"]".equals(tokens.get(i++)))
                return "ERROR: Falta ']' en valor condición.";

            CSVDatabaseManager db = new CSVDatabaseManager();
            boolean ok = db.rewriteRegistros(user.getBaseActiva(), tabla, columnas, valores, colCond, valCond);
            enviarMensaje("REWRITE tabla " + tabla + " set " + columnas + "=" + valores + " where " + colCond + "=" + valCond);
            return ok ? "OK: Registros actualizados." : "ERROR: No se pudieron actualizar registros.";

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: Fallo de sintaxis en REWRITE.";
        }
    }
    
    private void enviarMensaje(String msg) {
        if (messageCallback != null) {
            messageCallback.accept(msg);
        } else {
            System.out.println(msg);
        }
    }
}
