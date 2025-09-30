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

    // 🔹 login devuelve token o null si falla
    public String login(String username, String password) {
        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("LOGIN " + username + " " + password);
            String respuesta = in.readLine();
            if (respuesta != null && respuesta.startsWith("OK")) {
                return respuesta.split(" ")[1]; // token
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // 🔹 enviar consulta usando token
    public String enviarConsultaConToken(String token, String consulta) {
        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("QUERY " + token + " " + consulta);
            return in.readLine();

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: no se pudo conectar al servidor.";
        }
    }
}
