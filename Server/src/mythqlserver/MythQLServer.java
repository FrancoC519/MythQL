package mythqlserver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class MythQLServer {
    public static void main(String[] args) {
        int port = 12345;
        UserStore userStore = new UserStore();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Servidor MythQL escuchando en puerto " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Cliente conectado: " + clientSocket.getInetAddress());

                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                // --- Autenticación ---

                String username = in.readLine();
                System.out.println(username);
                String password = in.readLine();
                System.out.println(password);
                
                User user = userStore.authenticate(username, password);

                if (user == null) {
                    out.println("ERROR: Autenticación fallida.");
                    continue;
                }

                out.println("OK : Sesión iniciada como " + user.getUsername() + " con roles " + user.getRoles());

                // --- Procesar consultas ---
                String consulta;
                GestorConsultas gestor = new GestorConsultas();
                while ((consulta = in.readLine()) != null) {
                    System.out.println("Consulta recibida: " + consulta);
                    String respuesta = gestor.procesarConsulta(consulta);
                    out.println(respuesta);
                }

                clientSocket.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}