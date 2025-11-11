package mythqlserver;

import java.io.*;
import java.util.*;

public class UserStore {
    private static final String FILE = "src/users.csv";
    private List<User> users = new ArrayList<>();

    public UserStore() {
        load();
    }

    private void load() {
        try (BufferedReader br = new BufferedReader(new FileReader(FILE))) {
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; }
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

    private void save() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(FILE))) {
            pw.println("username,password,roles");
            for (User user : users) {
                String rolesStr = String.join(";", user.getRoles());
                pw.println(user.getUsername() + "," + user.getPassword() + "," + rolesStr);
            }
        } catch (Exception e) {
            System.err.println("Error guardando users.csv: " + e.getMessage());
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

    // ========== NUEVOS MÉTODOS PARA GESTIÓN DE USUARIOS ==========
    
    public boolean crearUsuario(String username, String password, String role) {
        for (User user : users) {
            if (user.getUsername().equalsIgnoreCase(username)) {
                return false;
            }
        }
        
        List<String> roles = new ArrayList<>();
        roles.add(role.toUpperCase());
        User nuevoUsuario = new User(username, password, roles);
        users.add(nuevoUsuario);
        
        save();
        return true;
    }
    
    public boolean eliminarUsuario(String username) {
        User usuarioAEliminar = null;
        for (User user : users) {
            if (user.getUsername().equalsIgnoreCase(username)) {
                usuarioAEliminar = user;
                break;
            }
        }
        
        if (usuarioAEliminar != null) {
            users.remove(usuarioAEliminar);
            save();
            return true;
        }
        return false;
    }
    
    public boolean agregarPrivilegio(String username, String privilegio) {
        for (User user : users) {
            if (user.getUsername().equalsIgnoreCase(username)) {
                List<String> roles = user.getRoles();
                if (!roles.contains(privilegio.toUpperCase())) {
                    roles.add(privilegio.toUpperCase());
                    save();
                    return true;
                }
                return true;
            }
        }
        return false;
    }
    
    public boolean quitarPrivilegio(String username, String privilegio) {
        for (User user : users) {
            if (user.getUsername().equalsIgnoreCase(username)) {
                List<String> roles = user.getRoles();
                if (roles.remove(privilegio.toUpperCase())) {
                    save();
                    return true;
                }
                return true;
            }
        }
        return false;
    }
    
    public User obtenerUsuario(String username) {
        for (User user : users) {
            if (user.getUsername().equalsIgnoreCase(username)) {
                return user;
            }
        }
        return null;
    }
    
    public List<User> listarUsuarios() {
        return new ArrayList<>(users);
    }
}