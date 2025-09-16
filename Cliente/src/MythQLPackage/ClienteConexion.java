/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package MythQLPackage;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClienteConexion {
    private String host;
    private int port;

    public ClienteConexion(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String enviarConsulta(String consulta) {
        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Enviar consulta
            out.println(consulta);

            // Leer respuesta del servidor
            return in.readLine();

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: No se pudo conectar con el servidor.";
        }
    }
}