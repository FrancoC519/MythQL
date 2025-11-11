package mythqlserver;

import java.util.List;

public class User {
    private String username;
    private String password;
    private List<String> roles;
    private String baseActiva;
    private String token;

    public User(String username, String password, List<String> roles) {
        this.username = username;
        this.password = password;
        this.roles = roles;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public List<String> getRoles() { return roles; }
    public String getToken() { return token; }

    public boolean tienePrivilegio(String privilegio) {
        return roles.contains(privilegio.toUpperCase());
    }

    public boolean tieneRol(String rol) {
        return roles.contains(rol.toUpperCase());
    }

    @Override
    public String toString() {
        return "User{" + "username='" + username + '\'' + ", roles=" + roles + '}';
    }
    
    public String getBaseActiva() {
        return baseActiva;
    }

    public void setBaseActiva(String baseActiva) {
        this.baseActiva = baseActiva;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
}