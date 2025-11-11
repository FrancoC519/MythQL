package mythqlserver;
import java.io.File;
import java.util.*;
import java.util.regex.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class GestorConsultas {
    private final String dbPath = "Databases/";
    private Consumer<String> messageCallback;
    private Consumer<String> notificacionCallback;
    private TransactionManager transactionManager;
    private UserStore userStore;

    public GestorConsultas() {
        this.messageCallback = null;
        this.notificacionCallback = null;
        this.transactionManager = null;
        this.userStore = new UserStore();
    }

    public GestorConsultas(Consumer<String> callback) {
        this.messageCallback = callback;
        this.notificacionCallback = null;
        this.transactionManager = null;
        this.userStore = new UserStore();
    }

    public GestorConsultas(Consumer<String> msgCallback, Consumer<String> notifCallback) {
        this.messageCallback = msgCallback;
        this.notificacionCallback = notifCallback;
        this.transactionManager = null;
        this.userStore = new UserStore();
    }

    public GestorConsultas(Consumer<String> msgCallback, Consumer<String> notifCallback, 
                          TransactionManager transactionManager) {
        this.messageCallback = msgCallback;
        this.notificacionCallback = notifCallback;
        this.transactionManager = transactionManager;
        this.userStore = new UserStore();
    }

    public String procesarConsulta(String consulta, User user) {
        List<String> tokens = tokenizar(consulta);
        if (tokens.isEmpty()) return "ERROR: Consulta vacía.";
        
        String comando = tokens.get(0).toUpperCase();
        enviarMensaje("Comando recibido: " + comando);
        
        // ANTES de ejecutar comandos que modifican, hacer backup si hay transacción activa
        if (esConsultaQueModifica(comando) && transactionManager != null) {
            String database = user.getBaseActiva();
            String table = extraerTablaDeConsulta(tokens, comando);
            transactionManager.backupBeforeModification(user.getToken(), database, table);
        }
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
            // ========== NUEVOS COMANDOS DCL ==========
            case "INVOKE": return comandoInvoke(tokens, user);
            case "EMPOWER": return comandoEmpower(tokens, user);
            case "DISARM": return comandoDisarm(tokens, user);
            default: return "ERROR: Comando desconocido '" + comando + "'";
        }
    }
    
    private String extraerTablaDeConsulta(List<String> tokens, String comando) {
        switch (comando) {
            case "SUMMON":
                if (tokens.size() > 2 && "TABLE".equals(tokens.get(1))) {
                    return tokens.get(2);
                }
                break;
            case "BURN":
                if (tokens.size() > 2 && "TABLE".equals(tokens.get(1))) {
                    return tokens.get(2);
                }
                break;
            case "FILE":
                if (tokens.size() > 1) {
                    return tokens.get(1);
                }
                break;
            case "MORPH":
                if (tokens.size() > 1) {
                    return tokens.get(1);
                }
                break;
            case "SWEEP":
                if (tokens.size() > 1) {
                    return tokens.get(1);
                }
                break;
            case "REWRITE":
                if (tokens.size() > 1) {
                    return tokens.get(1);
                }
                break;
        }
        return null;
    }
    
    private boolean esConsultaQueModifica(String comando) {
        return "SUMMON".equals(comando) || "BURN".equals(comando) || "FILE".equals(comando) ||
               "MORPH".equals(comando) || "SWEEP".equals(comando) || "REWRITE".equals(comando);
    }

    // ========== NUEVOS COMANDOS DCL ==========

    private String comandoInvoke(List<String> tokens, User user) {
        // INVOKE USER username { password, role }
        if (tokens.size() != 8) {
            return "ERROR: Sintaxis incorrecta en INVOKE. Uso: INVOKE USER username { password, role }";
        }

        if (!"USER".equals(tokens.get(1))) {
            return "ERROR: INVOKE debe ser seguido de USER";
        }

        String username = tokens.get(2);
        if (!username.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return "ERROR: Nombre de usuario inválido: " + username;
        }

        if (!"{".equals(tokens.get(3))) {
            return "ERROR: Falta '{' después del nombre de usuario";
        }

        String password = tokens.get(4);
        // Remover comillas si las tiene
        if ((password.startsWith("\"") && password.endsWith("\"")) ||
            (password.startsWith("'") && password.endsWith("'"))) {
            password = password.substring(1, password.length() - 1);
        }

        if (!",".equals(tokens.get(5))) {
            return "ERROR: Falta ',' entre contraseña y rol";
        }

        String role = tokens.get(6);
        if (!role.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return "ERROR: Nombre de rol inválido: " + role;
        }

        if (!"}".equals(tokens.get(7))) {
            return "ERROR: Falta '}' al final del comando INVOKE";
        }

        // Validar rol
        List<String> rolesValidos = List.of("READER", "WRITER", "MANAGER", "ADMIN", "OWNER");
        if (!rolesValidos.contains(role.toUpperCase())) {
            return "ERROR: Rol inválido: " + role + ". Roles válidos: " + rolesValidos;
        }

        // Verificar permisos del usuario actual
        if (!tienePermisoAdministrativo(user)) {
            return "ERROR: No tiene permisos para crear usuarios";
        }

        // Crear usuario
        boolean exito = userStore.crearUsuario(username, password, role.toUpperCase());
        
        if (exito) {
            enviarMensaje("Usuario creado: " + username + " con rol: " + role);
            if (notificacionCallback != null) {
                notificacionCallback.accept("Usuario '" + username + "' creado por " + user.getUsername());
            }
            return "OK: Usuario '" + username + "' creado con rol " + role;
        } else {
            return "ERROR: No se pudo crear el usuario '" + username + "' (¿ya existe?)";
        }
    }

    private String comandoEmpower(List<String> tokens, User user) {
        // EMPOWER username { privilegio1, privilegio2, ... }
        if (tokens.size() < 5) {
            return "ERROR: Sintaxis incorrecta en EMPOWER. Uso: EMPOWER username { privilegio1, privilegio2, ... }";
        }

        String username = tokens.get(1);
        if (!username.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return "ERROR: Nombre de usuario inválido: " + username;
        }

        if (!"{".equals(tokens.get(2))) {
            return "ERROR: Falta '{' después del nombre de usuario";
        }

        // Verificar permisos del usuario actual
        if (!tienePermisoAdministrativo(user)) {
            return "ERROR: No tiene permisos para otorgar privilegios";
        }

        List<String> privilegios = new ArrayList<>();
        int i = 3;
        
        // Lista de privilegios válidos
        List<String> privilegiosValidos = List.of(
            "SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", 
            "GRANT", "REVOKE", "BACKUP", "RESTORE", "ALL"
        );

        while (i < tokens.size() && !"}".equals(tokens.get(i))) {
            String privilegio = tokens.get(i);
            
            if (!privilegiosValidos.contains(privilegio.toUpperCase())) {
                return "ERROR: Privilegio inválido: " + privilegio + 
                       ". Privilegios válidos: " + privilegiosValidos;
            }
            
            privilegios.add(privilegio.toUpperCase());
            i++;
            
            // Si hay coma, saltarla
            if (i < tokens.size() && ",".equals(tokens.get(i))) {
                i++;
            }
        }

        if (i >= tokens.size() || !"}".equals(tokens.get(i))) {
            return "ERROR: Falta '}' al final del comando EMPOWER";
        }

        if (privilegios.isEmpty()) {
            return "ERROR: Debe especificar al menos un privilegio";
        }

        // Aplicar privilegios al usuario
        boolean todosExitosos = true;
        List<String> privilegiosAplicados = new ArrayList<>();
        
        for (String privilegio : privilegios) {
            boolean exito = userStore.agregarPrivilegio(username, privilegio);
            if (exito) {
                privilegiosAplicados.add(privilegio);
            } else {
                todosExitosos = false;
            }
        }

        if (todosExitosos) {
            enviarMensaje("Privilegios otorgados a " + username + ": " + privilegios);
            if (notificacionCallback != null) {
                notificacionCallback.accept("Privilegios otorgados a '" + username + "' por " + user.getUsername());
            }
            return "OK: Privilegios otorgados a '" + username + "': " + String.join(", ", privilegios);
        } else {
            return "ERROR: Algunos privilegios no se pudieron otorgar. Aplicados: " + String.join(", ", privilegiosAplicados);
        }
    }

    private String comandoDisarm(List<String> tokens, User user) {
        // DISARM username { privilegio1, privilegio2, ... }
        if (tokens.size() < 5) {
            return "ERROR: Sintaxis incorrecta en DISARM. Uso: DISARM username { privilegio1, privilegio2, ... }";
        }

        String username = tokens.get(1);
        if (!username.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return "ERROR: Nombre de usuario inválido: " + username;
        }

        if (!"{".equals(tokens.get(2))) {
            return "ERROR: Falta '{' después del nombre de usuario";
        }

        // Verificar permisos del usuario actual
        if (!tienePermisoAdministrativo(user)) {
            return "ERROR: No tiene permisos para quitar privilegios";
        }

        List<String> privilegios = new ArrayList<>();
        int i = 3;
        
        // Lista de privilegios válidos
        List<String> privilegiosValidos = List.of(
            "SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", 
            "GRANT", "REVOKE", "BACKUP", "RESTORE", "ALL"
        );

        while (i < tokens.size() && !"}".equals(tokens.get(i))) {
            String privilegio = tokens.get(i);
            
            if (!privilegiosValidos.contains(privilegio.toUpperCase())) {
                return "ERROR: Privilegio inválido: " + privilegio + 
                       ". Privilegios válidos: " + privilegiosValidos;
            }
            
            privilegios.add(privilegio.toUpperCase());
            i++;
            
            // Si hay coma, saltarla
            if (i < tokens.size() && ",".equals(tokens.get(i))) {
                i++;
            }
        }

        if (i >= tokens.size() || !"}".equals(tokens.get(i))) {
            return "ERROR: Falta '}' al final del comando DISARM";
        }

        if (privilegios.isEmpty()) {
            return "ERROR: Debe especificar al menos un privilegio";
        }

        // Quitar privilegios al usuario
        boolean todosExitosos = true;
        List<String> privilegiosRemovidos = new ArrayList<>();
        
        for (String privilegio : privilegios) {
            boolean exito = userStore.quitarPrivilegio(username, privilegio);
            if (exito) {
                privilegiosRemovidos.add(privilegio);
            } else {
                todosExitosos = false;
            }
        }

        if (todosExitosos) {
            enviarMensaje("Privilegios removidos de " + username + ": " + privilegios);
            if (notificacionCallback != null) {
                notificacionCallback.accept("Privilegios removidos de '" + username + "' por " + user.getUsername());
            }
            return "OK: Privilegios removidos de '" + username + "': " + String.join(", ", privilegios);
        } else {
            return "ERROR: Algunos privilegios no se pudieron remover. Removidos: " + String.join(", ", privilegiosRemovidos);
        }
    }

    private boolean tienePermisoAdministrativo(User user) {
        // Verificar si el usuario tiene permisos de administración
        List<String> roles = user.getRoles();
        return roles.contains("ADMIN") || roles.contains("OWNER") || 
               roles.contains("SUPERUSER") || roles.stream().anyMatch(r -> r.contains("GRANT"));
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

    // ========== TOKENIZADOR ==========
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

        // ====== CREAR DATABASE ======
        if ("DATABASE".equals(tokens.get(1))) {
            String nombreDB = tokens.get(2);
            boolean exito = db.crearDatabase(nombreDB);
            msg = exito ? "OK: Base de datos '" + nombreDB + "' creada."
                        : "ERROR: No se pudo crear la base de datos.";
            enviarMensaje(msg);
            if (exito && notificacionCallback != null)
                notificacionCallback.accept("Base de datos '" + nombreDB + "' creada por " + user.getUsername());
            return msg;
        }

        // ====== CREAR TABLE ======
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

            // Extraer atributos
            for (int i = 4; i < tokens.size(); i++) {
                if ("}".equals(tokens.get(i))) break;
                atributos.add(tokens.get(i));
            }

            enviarMensaje("SUMMON TABLE: " + nombreTabla + " en " + user.getBaseActiva() +
                          " con atributos: " + atributos + " por usuario: " + user.getUsername());

            boolean exito = db.crearTabla(user.getBaseActiva(), nombreTabla, atributos);
            msg = exito ? "OK: Tabla '" + nombreTabla + "' creada en DB " + user.getBaseActiva()
                        : "ERROR: No se pudo crear la tabla.";
            enviarMensaje(msg);

            if (exito && notificacionCallback != null)
                notificacionCallback.accept("Tabla '" + nombreTabla + "' creada en " +
                                            user.getBaseActiva() + " por " + user.getUsername());

            return msg;
        }

        msg = "ERROR: SUMMON debe ser DATABASE o TABLE.";
        enviarMensaje(msg);
        return msg;
    }

    private String comandoBring(List<String> tokens, User user) {
        if (tokens.size() < 2)
            return "ERROR: Sintaxis BRING inválida. Uso: BRING <tabla> [ { columnas } ] [IN WHICH <condiciones>]";

        if (user.getBaseActiva() == null)
            return "ERROR: No hay base activa.";

        String dbName = user.getBaseActiva();

        try {
            // === Parsear tablas y columnas ===
            int i = 1;
            List<String> tablas = new ArrayList<>();
            Map<String, List<String>> columnasPorTabla = new HashMap<>();
            Map<String, List<String>> datosTablas = new HashMap<>();
            Map<String, List<String>> definicionesTablas = new HashMap<>();
            Map<String, List<String>> nombresColumnasTablas = new HashMap<>();

            // Parsear tablas
            while (i < tokens.size()) {
                String token = tokens.get(i);

                if (i < tokens.size() - 1 && "IN".equalsIgnoreCase(token) && "WHICH".equalsIgnoreCase(tokens.get(i + 1))) {
                    break;
                }

                if (token.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    String tableName = token;
                    tablas.add(tableName);

                    // Verificar que la tabla existe
                    File tablaFile = new File(dbPath + dbName + "_tables/" + tableName + ".csv");
                    if (!tablaFile.exists())
                        return "ERROR: Tabla '" + tableName + "' no encontrada.";

                    // Leer definición de tabla
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
                    definicionesTablas.put(tableName, List.of(definicion.split(",")));

                    // Extraer nombres de columnas
                    Pattern defPat = Pattern.compile("(\\w+)\\s+(INT|VARCHAR|DATE|BOOL|FLOAT)(?:\\s*\\(\\s*\\d+\\s*\\))?", Pattern.CASE_INSENSITIVE);
                    Matcher m = defPat.matcher(definicion);
                    List<String> nombresColumnas = new ArrayList<>();
                    while (m.find()) {
                        nombresColumnas.add(m.group(1).toUpperCase());
                    }
                    if (nombresColumnas.isEmpty())
                        return "ERROR: No se pudieron extraer las columnas de la definición de '" + tableName + "'.";
                    nombresColumnasTablas.put(tableName, nombresColumnas);

                    // Leer datos de la tabla
                    List<String> lineas = java.nio.file.Files.readAllLines(tablaFile.toPath());
                    datosTablas.put(tableName, lineas);

                    // Parsear columnas específicas si las hay
                    List<String> columnasPedidas = new ArrayList<>();
                    i++;
                    if (i < tokens.size() && "{".equals(tokens.get(i))) {
                        i++;
                        while (i < tokens.size() && !"}".equals(tokens.get(i))) {
                            String col = tokens.get(i).replace(",", "").trim().toUpperCase();
                            if (!col.isEmpty()) columnasPedidas.add(col);
                            i++;
                        }
                        if (i < tokens.size() && "}".equals(tokens.get(i))) i++;
                    } else {
                        columnasPedidas.addAll(nombresColumnas);
                    }
                    columnasPorTabla.put(tableName, columnasPedidas);

                    if (i < tokens.size() && ",".equals(tokens.get(i))) {
                        i++;
                    }
                } else {
                    i++;
                }
            }

            // === Parsear condiciones IN WHICH ===
            List<String> condicionesJoin = new ArrayList<>();
            String condicionWhere = null;
            String operadorWhere = null;
            String valorWhere = null;

            if (i < tokens.size() && "IN".equalsIgnoreCase(tokens.get(i)) && i + 1 < tokens.size() && "WHICH".equalsIgnoreCase(tokens.get(i + 1))) {
                i += 2;

                while (i < tokens.size()) {
                    if (i + 6 >= tokens.size())
                        return "ERROR: Sintaxis incompleta en IN WHICH";

                    // Parsear: Tabla { Columna } operador ...
                    String leftTabla = tokens.get(i);
                    String leftBraceOpen = tokens.get(i + 1);
                    String leftColumna = tokens.get(i + 2);
                    String leftBraceClose = tokens.get(i + 3);

                    if (!"{".equals(leftBraceOpen) || !"}".equals(leftBraceClose))
                        return "ERROR: Formato inválido. Use: Tabla { Columna }";

                    String leftSide = leftTabla + "{" + leftColumna + "}";
                    i += 4;

                    String operador = tokens.get(i++).toUpperCase();

                    // Verificar si es condición WHERE [valor] o JOIN Tabla { Columna }
                    if (i < tokens.size() && "[".equals(tokens.get(i))) {
                        // WHERE condition
                        i++; // saltar [
                        StringBuilder sb = new StringBuilder();
                        while (i < tokens.size() && !"]".equals(tokens.get(i))) {
                            sb.append(tokens.get(i)).append(" ");
                            i++;
                        }
                        if (i >= tokens.size() || !"]".equals(tokens.get(i)))
                            return "ERROR: Falta ']' de cierre";

                        String valor = sb.toString().trim();
                        i++; // saltar ]

                        condicionWhere = leftSide;
                        operadorWhere = operador;
                        valorWhere = valor;
                    } else {
                        // JOIN condition: Tabla { Columna }
                        if (i + 3 >= tokens.size())
                            return "ERROR: Sintaxis incompleta en condición JOIN";

                        String rightTabla = tokens.get(i++);
                        String rightBraceOpen = tokens.get(i++);
                        String rightColumna = tokens.get(i++);
                        String rightBraceClose = tokens.get(i++);

                        if (!"{".equals(rightBraceOpen) || !"}".equals(rightBraceClose))
                            return "ERROR: Formato inválido. Use: Tabla { Columna }";

                        String rightSide = rightTabla + "{" + rightColumna + "}";
                        condicionesJoin.add(leftSide + " " + operador + " " + rightSide);
                    }

                    // Verificar AND
                    if (i < tokens.size() && "AND".equalsIgnoreCase(tokens.get(i))) {
                        i++;
                    } else {
                        break;
                    }
                }
            }

            // === Realizar INNER JOIN si hay múltiples tablas ===
            List<Map<String, String>> resultadoJoin = new ArrayList<>();

            if (tablas.size() == 1) {
                // Caso simple: una sola tabla
                String tableName = tablas.get(0);
                List<String> lineas = datosTablas.get(tableName);
                List<String> nombresColumnas = nombresColumnasTablas.get(tableName);

                for (String linea : lineas) {
                    String[] valores = linea.split(",", -1);
                    Map<String, String> fila = new HashMap<>();

                    for (int c = 0; c < nombresColumnas.size() && c < valores.length; c++) {
                        String val = normalizarValor(valores[c]);
                        fila.put(tableName + "." + nombresColumnas.get(c), val);
                    }

                    // Aplicar filtro WHERE si existe
                    if (condicionWhere != null && !aplicarFiltro(fila, condicionWhere, operadorWhere, valorWhere)) {
                        continue;
                    }

                    resultadoJoin.add(fila);
                }
            } else {
                // INNER JOIN múltiples tablas
                resultadoJoin = realizarInnerJoin(tablas, datosTablas, nombresColumnasTablas, condicionesJoin);

                // Aplicar filtro WHERE si existe
                if (condicionWhere != null) {
                    List<Map<String, String>> resultadoFiltrado = new ArrayList<>();
                    for (Map<String, String> fila : resultadoJoin) {
                        if (aplicarFiltro(fila, condicionWhere, operadorWhere, valorWhere)) {
                            resultadoFiltrado.add(fila);
                        }
                    }
                    resultadoJoin = resultadoFiltrado;
                }
            }

            // === Construir salida ===
            if (resultadoJoin.isEmpty()) {
                return "(sin resultados)";
            }

            StringBuilder sb = new StringBuilder();

            // Encabezado con nombres de tablas y columnas
            List<String> encabezados = new ArrayList<>();
            for (String tabla : tablas) {
                for (String col : columnasPorTabla.get(tabla)) {
                    encabezados.add(tabla + "." + col);
                }
            }
            sb.append(String.join(" \\ ", encabezados)).append("||");

            // Datos
            for (Map<String, String> fila : resultadoJoin) {
                List<String> valoresFila = new ArrayList<>();
                for (String tabla : tablas) {
                    for (String col : columnasPorTabla.get(tabla)) {
                        String clave = tabla + "." + col.toUpperCase();
                        String valor = fila.getOrDefault(clave, "null");
                        valoresFila.add(valor);
                    }
                }
                sb.append(String.join(" | ", valoresFila)).append("\\");
            }

            return sb.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: No se pudo ejecutar el comando BRING.";
        }
    }

    // === Método para realizar INNER JOIN ===
    private List<Map<String, String>> realizarInnerJoin(List<String> tablas, 
                                                       Map<String, List<String>> datosTablas,
                                                       Map<String, List<String>> nombresColumnasTablas,
                                                       List<String> condicionesJoin) {
        List<Map<String, String>> resultado = new ArrayList<>();

        if (tablas.isEmpty()) return resultado;

        // Empezar con la primera tabla
        String primeraTabla = tablas.get(0);
        for (String linea : datosTablas.get(primeraTabla)) {
            String[] valores = linea.split(",", -1);
            Map<String, String> filaBase = new HashMap<>();

            for (int c = 0; c < nombresColumnasTablas.get(primeraTabla).size() && c < valores.length; c++) {
                String val = normalizarValor(valores[c]);
                filaBase.put(primeraTabla + "." + nombresColumnasTablas.get(primeraTabla).get(c), val);
            }

            resultado.add(filaBase);
        }

        // Unir con las demás tablas
        for (int t = 1; t < tablas.size(); t++) {
            String tablaActual = tablas.get(t);
            List<Map<String, String>> nuevoResultado = new ArrayList<>();

            for (Map<String, String> filaExistente : resultado) {
                for (String linea : datosTablas.get(tablaActual)) {
                    String[] valores = linea.split(",", -1);
                    Map<String, String> filaCombinada = new HashMap<>(filaExistente);

                    // Agregar datos de la tabla actual
                    for (int c = 0; c < nombresColumnasTablas.get(tablaActual).size() && c < valores.length; c++) {
                        String val = normalizarValor(valores[c]);
                        filaCombinada.put(tablaActual + "." + nombresColumnasTablas.get(tablaActual).get(c), val);
                    }

                    // Verificar condiciones de JOIN
                    boolean cumpleCondiciones = true;
                    for (String condicion : condicionesJoin) {
                        String[] partes = condicion.split(" ");
                        if (partes.length >= 3) {
                            String leftCol = partes[0]; // Formato: Tabla{Columna}
                            String operador = partes[1];
                            String rightCol = partes[2]; // Formato: Tabla{Columna}

                            String leftTabla = leftCol.substring(0, leftCol.indexOf("{"));
                            String leftCampo = leftCol.substring(leftCol.indexOf("{") + 1, leftCol.indexOf("}"));
                            String rightTabla = rightCol.substring(0, rightCol.indexOf("{"));
                            String rightCampo = rightCol.substring(rightCol.indexOf("{") + 1, rightCol.indexOf("}"));

                            String valorLeft = filaCombinada.get(leftTabla + "." + leftCampo.toUpperCase());
                            String valorRight = filaCombinada.get(rightTabla + "." + rightCampo.toUpperCase());

                            if (valorLeft == null || valorRight == null || 
                                !cumpleCondicion(valorLeft, operador, valorRight)) {
                                cumpleCondiciones = false;
                                break;
                            }
                        }
                    }

                    if (cumpleCondiciones) {
                        nuevoResultado.add(filaCombinada);
                    }
                }
            }

            resultado = nuevoResultado;
        }

        return resultado;
    }

    // === Helper para aplicar filtro WHERE ===
    private boolean aplicarFiltro(Map<String, String> fila, String condicionWhere, String operador, String valor) {
        // Parsear Tabla{Columna}
        String tabla = condicionWhere.substring(0, condicionWhere.indexOf("{"));
        String columna = condicionWhere.substring(condicionWhere.indexOf("{") + 1, condicionWhere.indexOf("}"));

        String valorCampo = fila.get(tabla + "." + columna.toUpperCase());
        if (valorCampo == null) return false;

        return cumpleCondicion(valorCampo, operador, valor);
    }

    // === Helper para normalizar valores ===
    private String normalizarValor(String valor) {
        return valor.trim()
                .replaceAll("^\"|\"$", "")
                .replaceAll("^'|'$", "")
                .replaceAll("(?i)null", "");
    }

    // === Helper para condiciones (el mismo que tenías) ===
    private boolean cumpleCondicion(String valorCampo, String op, String valorComparar) {
        if (valorCampo == null || valorComparar == null) {
            return false;
        }

        if (op.equals("GREATER") || op.equals("LESS")) {
            try {
                double numCampo = Double.parseDouble(valorCampo);
                double numComparar = Double.parseDouble(valorComparar);
                switch (op.toUpperCase()) {
                    case "GREATER": return numCampo > numComparar;
                    case "LESS": return numCampo < numComparar;
                    default: return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }

        switch (op.toUpperCase()) {
            case "EQUALS": return valorCampo.equalsIgnoreCase(valorComparar);
            case "NOTEQUALS": return !valorCampo.equalsIgnoreCase(valorComparar);
            case "CONTAINS": return valorCampo.toLowerCase().contains(valorComparar.toLowerCase());
            default: return false;
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