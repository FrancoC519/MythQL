package MythQLPackage;

import java.io.*;
import java.net.Socket;

public class ClienteConexion {
    private String host;
    private int port;

    public ClienteConexion(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // Método para testear conexión
    public boolean testConexion() {
        try (Socket socket = new Socket(host, port)) {
            return socket.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    public String login(String username, String password) {
        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("LOGIN " + username + " " + password);
            String respuesta = in.readLine();
            if (respuesta != null && respuesta.startsWith("OK")) {
                return respuesta.split(" ")[1];
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String enviarConsultaConToken(String token, String consulta) {
        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            if (consulta.startsWith("LOGOUT")){
                out.println(consulta);
            } else {
                out.println("QUERY " + token + " " + consulta);
            }
            return in.readLine();

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: no se pudo conectar al servidor.";
        }
    }
}