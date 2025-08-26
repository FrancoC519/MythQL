package MythQLPackage;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class UserStore {
    private static final String FILE = "C:\\Users\\gabri\\OneDrive\\Documentos\\NetBeansProjects\\MythQL\\src\\main\\java\\MythQLPackage\\users.csv";
    private List<User> users = new ArrayList<>();

    public UserStore() {
        load();
    }

    private void load() {
        try (BufferedReader br = Files.newBufferedReader(Paths.get(FILE))) {
            String line;
            boolean firstLine = true; // saltar encabezado
            while ((line = br.readLine()) != null) {
                if (firstLine) { 
                    firstLine = false;
                    continue; 
                }
                String[] parts = line.split(",", -1);
                if (parts.length >= 3) {
                    String username = parts[0].trim();
                    String password = parts[1].trim();
                    List<String> roles = Arrays.asList(parts[2].split(";"));
                    users.add(new User(username, password, roles));
                }
            }
        } catch (IOException e) {
            System.err.println("Error cargando users.csv: " + e.getMessage());
        }
    }

    public User authenticate(String username, String password) {
        for (User user : users) {
            if (user.getUsername().equals(username) && user.getPassword().equals(password)) {
                return user;
            }
        }
        return null;
    }
}