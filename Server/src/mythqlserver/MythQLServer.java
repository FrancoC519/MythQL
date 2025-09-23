package mythqlserver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class MythQLServer {
    public static void main(String[] args) {
        int port = 12345;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Servidor MythQL escuchando en puerto " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Cliente conectado: " + clientSocket.getInetAddress());

                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                String consulta = in.readLine();
                System.out.println("Consulta recibida: " + consulta);

                GestorConsultas gestor = new GestorConsultas();
                String respuesta = gestor.procesarConsulta(consulta);

                out.println(respuesta);

                clientSocket.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}