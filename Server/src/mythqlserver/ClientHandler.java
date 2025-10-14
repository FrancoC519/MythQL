package mythqlserver;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.function.Consumer;

public class ClientHandler implements Runnable {
    private Consumer<String> messageCallback;
    private Socket socket;
    private UserStore userStore;
    private Map<String, User> sesiones;
    private String tokenAsignado = null;
    private String token;
    private GestorConsultas gc;
    private MythQLServer server;
    private PrintWriter out;
    private String clientId;

    public ClientHandler(Socket socket, UserStore userStore, Map<String, User> sesiones) {
        this(socket, userStore, sesiones, null);
    }

    public ClientHandler(Socket socket, UserStore userStore, Map<String, User> sesiones, Consumer<String> callback) {
        this.socket = socket;
        this.userStore = userStore;
        this.sesiones = sesiones;
        this.messageCallback = callback;
        this.clientId = socket.getInetAddress() + ":" + socket.getPort();
    }

    public void setServer(MythQLServer server) {
        this.server = server;
        this.gc = new GestorConsultas(
            messageCallback, 
            this::enviarNotificacionGlobal
        );
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
             PrintWriter outWriter = new PrintWriter(socket.getOutputStream(), true)) {
            
            this.out = outWriter;

            String mensaje;
            while ((mensaje = in.readLine()) != null) {
                enviarMensaje("Recibido: " + mensaje);

                String[] partes = mensaje.split(" ", 3);
                if (partes.length < 1) {
                    out.println("ERROR formato invalido");
                    continue;
                }
                String comando = partes[0].toUpperCase();

                switch (comando) {
                    case "LOGIN":
                        if (partes.length < 3) {
                            out.println("ERROR Uso: LOGIN usuario contrasena");
                            break;
                        }
                        manejarLogin(partes[1], partes[2], out);
                        break;

                    case "QUERY":
                        if (partes.length < 3) {
                            out.println("ERROR Uso: QUERY token consulta");
                            break;
                        }
                        manejarConsulta(partes[1], partes[2], out);
                        break;

                    case "LOGOUT":
                        if (partes.length < 2) {
                            out.println("ERROR Uso: LOGOUT token");
                            break;
                        }
                        manejarLogout(partes[1], out);
                        return;

                    case "SUBSCRIBE":
                        if (partes.length < 2) {
                            out.println("ERROR Uso: SUBSCRIBE token");
                            break;
                        }
                        manejarSubscribe(partes[1], out);
                        break;

                    case "GET_SCHEMAS":
                        if (partes.length < 2) {
                            out.println("ERROR Uso: GET_SCHEMAS token");
                            break;
                        }
                        manejarGetSchemas(partes[1], out);
                        break;

                    default:
                        out.println("ERROR Comando desconocido");
                }
            }
        } catch (Exception e) {
            enviarMensaje("Error en cliente: " + e.getMessage());
        } finally {
            if (server != null && tokenAsignado != null) {
                server.desuscribirDeNotificaciones(this::enviarNotificacion);
            }
            if (server != null) {
                server.removerCliente(clientId);
            }
        }
    }

    private void manejarLogin(String username, String password, PrintWriter out) {
        User user = userStore.authenticate(username, password);
        if (user == null) {
            out.println("ERROR credenciales invalidas");
            return;
        }

        synchronized (sesiones) {
            boolean yaLogueado = sesiones.values().stream()
                    .anyMatch(u -> u.getUsername().equals(username));
            if (yaLogueado) {
                out.println("ERROR usuario ya logueado en otra sesion");
                return;
            }
            String token = UUID.randomUUID().toString();
            sesiones.put(token, user);
            tokenAsignado = token;
            out.println("OK " + token);
            enviarMensaje("Usuario autenticado: " + username + " -> Token: " + token);
        }
    }

    private void manejarConsulta(String token, String consulta, PrintWriter out) {
        User user = sesiones.get(token);
        this.token = token;

        if (user == null) {
            out.println("ERROR sesion no valida");
            return;
        }

        String resultado = gc.procesarConsulta(consulta, user);
        out.println("RESULT " + resultado);
        enviarMensaje("Consulta de " + user.getUsername() + ": " + consulta);
        enviarMensaje(resultado);

        if (esComandoQueCambiaDatos(consulta)) {
            String notificacion = "Cambio por " + user.getUsername() + ": " + 
                                extraerTipoCambio(consulta);
            if (server != null) {
                server.broadcastNotificacion(notificacion);
            }
        }
    }

    private void manejarLogout(String token, PrintWriter out) {
        User removed = sesiones.remove(token);
        if (removed != null) {
            out.println("OK Logout correcto");
            enviarMensaje("Usuario " + removed.getUsername() + " deslogueado (token " + token + ")");
        } else {
            out.println("ERROR token no valido");
        }
    }

    private void manejarSubscribe(String token, PrintWriter out) {
        User user = sesiones.get(token);
        if (user == null) {
            out.println("ERROR token invalido");
            return;
        }

        if (server != null) {
            server.suscribirANotificaciones(this::enviarNotificacion);
            out.println("OK Suscrito a notificaciones");
            enviarMensaje("Cliente " + user.getUsername() + " suscrito a notificaciones");
        } else {
            out.println("ERROR Servidor no disponible");
        }
    }

    private void manejarGetSchemas(String token, PrintWriter out) {
        User user = sesiones.get(token);
        if (user == null) {
            out.println("ERROR token invalido");
            return;
        }

        try {
            String esquemas = gc.obtenerEsquemasJerarquicos();
            out.println("SCHEMAS " + esquemas);
        } catch (Exception e) {
            out.println("ERROR " + e.getMessage());
        }
    }

    private void enviarNotificacion(String mensaje) {
        if (out != null) {
            out.println("NOTIFICATION " + mensaje);
        }
    }

    private void enviarNotificacionGlobal(String mensaje) {
        if (server != null) {
            server.broadcastNotificacion(mensaje);
        }
    }

    private boolean esComandoQueCambiaDatos(String consulta) {
        String comando = consulta.trim().toUpperCase();
        return comando.startsWith("SUMMON") || 
               comando.startsWith("BURN") || 
               comando.startsWith("FILE") ||
               comando.startsWith("CREATE") || 
               comando.startsWith("DROP") || 
               comando.startsWith("INSERT") ||
               comando.startsWith("UPDATE") || 
               comando.startsWith("DELETE");
    }

    private String extraerTipoCambio(String consulta) {
        String[] partes = consulta.trim().split("\\s+");
        if (partes.length < 2) return consulta;
        
        String comando = partes[0].toUpperCase();
        String objeto = partes[1].toUpperCase();
        
        switch (comando) {
            case "SUMMON":
                return "Creación: " + objeto + " " + (partes.length > 2 ? partes[2] : "");
            case "BURN":
                return "Eliminación: " + objeto + " " + (partes.length > 2 ? partes[2] : "");
            case "FILE":
                return "Inserción en tabla";
            default:
                return comando + " ejecutado";
        }
    }

    private void enviarMensaje(String msg) {
        if (messageCallback != null) {
            messageCallback.accept(msg);
        } else {
            System.out.println(msg);
        }
    }

    public User getUser() {
        return sesiones.get(token);
    }
}