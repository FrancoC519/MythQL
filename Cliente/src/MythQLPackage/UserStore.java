package MythQLPackage;

import java.io.*;
import java.util.*;
import java.io.InputStream;

public class UserStore {
    private static final String FILE = "/users.csv";
    private List<User> users = new ArrayList<>();

    public UserStore() {
        load();
    }

   private void load() {
        try (InputStream input = getClass().getResourceAsStream("/users.csv");
             BufferedReader br = new BufferedReader(new InputStreamReader(input))) {

            String line;
            boolean firstLine = true;

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
        } catch (Exception e) {
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