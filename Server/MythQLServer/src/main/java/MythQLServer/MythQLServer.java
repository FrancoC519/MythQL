package MythQLServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class MythQLServer {
    public static void main(String[] args) {
        int port = 12345; // Puerto fijo por ahora

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Servidor MythQL escuchando en puerto " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Cliente conectado: " + clientSocket.getInetAddress());

                // Streams de comunicación
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                // Leer consulta del cliente
                String consulta = in.readLine();
                System.out.println("Consulta recibida: " + consulta);

                // Procesar consulta
                GestorConsultas gestor = new GestorConsultas();
                String respuesta = gestor.procesarConsulta(consulta);

                // Devolver respuesta
                out.println(respuesta);

                // Cerrar conexión
                clientSocket.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
