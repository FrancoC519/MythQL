package mythqlserver;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class TransactionManager {
    private final String tempPath = "temp/";
    private final Map<String, UserTransactionState> userTransactions;
    private final Consumer<String> logCallback;
    
    public TransactionManager(Consumer<String> logCallback) {
        this.userTransactions = new ConcurrentHashMap<>();
        this.logCallback = logCallback;
        crearDirectorioTemp();
    }
    
    private void crearDirectorioTemp() {
        try {
            Files.createDirectories(Paths.get(tempPath));
            log("Directorio temporal creado: " + tempPath);
        } catch (IOException e) {
            log("Error creando directorio temp: " + e.getMessage());
        }
    }
    
    public boolean startTransaction(String token, User user) {
        try {
            UserTransactionState state = userTransactions.get(token);
            if (state != null && state.isActive()) {
                log("Ya hay una transacción activa para el token: " + token);
                return false; // Ya hay una transacción activa
            }
            
            int nextBackupNumber = getNextBackupNumber(token);
            String backupPath = tempPath + token + "/" + nextBackupNumber + "/";
            
            UserTransactionState newState = new UserTransactionState(token, user.getUsername(), backupPath, nextBackupNumber);
            userTransactions.put(token, newState);
            
            // Crear directorio de backup
            Files.createDirectories(Paths.get(backupPath));
            
            log("START transaction iniciado para usuario: " + user.getUsername() + ", backup: " + nextBackupNumber);
            return true;
            
        } catch (Exception e) {
            log("Error en START transaction: " + e.getMessage());
            return false;
        }
    }
    
    public boolean sealTransaction(String token) {
        try {
            UserTransactionState state = userTransactions.get(token);
            if (state == null || !state.isActive()) {
                log("No hay transacción activa para SEAL con token: " + token);
                return false;
            }
            
            state.setActive(false);
            log("SEAL transaction finalizado para usuario: " + state.getUsername() + ", backup: " + state.getBackupNumber());
            return true;
            
        } catch (Exception e) {
            log("Error en SEAL transaction: " + e.getMessage());
            return false;
        }
    }
    
    public boolean undoTransaction(String token, String undoNumberStr) {
        try {
            UserTransactionState state = userTransactions.get(token);
            if (state == null) {
                log("No hay estado de transacción para UNDO con token: " + token);
                return false;
            }
            
            int undoNumber;
            if (undoNumberStr == null || undoNumberStr.isEmpty()) {
                // Usar el backup más reciente
                undoNumber = getLatestBackupNumber(token);
            } else {
                try {
                    undoNumber = Integer.parseInt(undoNumberStr);
                } catch (NumberFormatException e) {
                    log("Número de backup inválido: " + undoNumberStr);
                    return false;
                }
            }
            
            String backupPath = tempPath + token + "/" + undoNumber + "/";
            File backupDir = new File(backupPath);
            
            if (!backupDir.exists()) {
                log("Backup no encontrado: " + backupPath);
                return false;
            }
            
            // Restaurar archivos desde el backup
            restoreFromBackup(backupPath);
            
            // Eliminar backups posteriores si existen
            deleteLaterBackups(token, undoNumber);
            
            log("UNDO ejecutado para usuario: " + state.getUsername() + ", backup: " + undoNumber);
            return true;
            
        } catch (Exception e) {
            log("Error en UNDO transaction: " + e.getMessage());
            return false;
        }
    }
    
    public void backupBeforeModification(String token, String database, String table) {
        try {
            UserTransactionState state = userTransactions.get(token);
            if (state == null || !state.isActive()) {
                return;
            }
            
            String backupPath = state.getBackupPath();
            String sourcePath = "Databases/";
            
            // Backup de la base de datos
            if (database != null) {
                String dbFile = sourcePath + database + ".csv";
                String backupDbFile = backupPath + database + ".csv";
                backupFileIfNotExists(dbFile, backupDbFile);
            }
            
            // Backup de la tabla
            if (database != null && table != null) {
                String tableDir = sourcePath + database + "_tables/";
                String tableFile = tableDir + table + ".csv";
                String backupTableDir = backupPath + database + "_tables/";
                String backupTableFile = backupTableDir + table + ".csv";
                
                backupFileIfNotExists(tableFile, backupTableFile);
            }
            
        } catch (Exception e) {
            log("Error en backup antes de modificación: " + e.getMessage());
        }
    }
    
    private void backupFileIfNotExists(String sourcePath, String backupPath) throws IOException {
        File sourceFile = new File(sourcePath);
        File backupFile = new File(backupPath);
        
        if (sourceFile.exists() && !backupFile.exists()) {
            Files.createDirectories(backupFile.getParentFile().toPath());
            Files.copy(sourceFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log("Backup creado: " + sourcePath + " -> " + backupPath);
        }
    }
    
    private void restoreFromBackup(String backupPath) throws IOException {
        File backupDir = new File(backupPath);
        if (!backupDir.exists()) return;
        
        // Copiar todos los archivos del backup al directorio real
        Files.walk(backupDir.toPath())
            .forEach(source -> {
                Path destination = Paths.get("Databases/").resolve(backupDir.toPath().relativize(source));
                try {
                    if (Files.isRegularFile(source)) {
                        Files.createDirectories(destination.getParent());
                        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                        log("Archivo restaurado: " + source + " -> " + destination);
                    }
                } catch (IOException e) {
                    log("Error restaurando archivo: " + e.getMessage());
                }
            });
    }
    
    private int getNextBackupNumber(String token) {
        String userDir = tempPath + token;
        File userDirFile = new File(userDir);
        
        if (!userDirFile.exists()) {
            return 1;
        }
        
        File[] backups = userDirFile.listFiles();
        if (backups == null || backups.length == 0) {
            return 1;
        }
        
        return Arrays.stream(backups)
                .filter(File::isDirectory)
                .map(File::getName)
                .filter(name -> name.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0) + 1;
    }
    
    private int getLatestBackupNumber(String token) {
        return getNextBackupNumber(token) - 1;
    }
    
    private void deleteLaterBackups(String token, int undoNumber) {
        String userDir = tempPath + token;
        File userDirFile = new File(userDir);
        
        if (!userDirFile.exists()) return;
        
        File[] backups = userDirFile.listFiles();
        if (backups == null) return;
        
        for (File backup : backups) {
            if (backup.isDirectory()) {
                try {
                    int backupNum = Integer.parseInt(backup.getName());
                    if (backupNum > undoNumber) {
                        deleteDirectory(backup);
                        log("Backup eliminado: " + backup.getPath());
                    }
                } catch (NumberFormatException e) {
                    // Ignorar directorios que no son números
                }
            }
        }
    }
    
    public void cleanupUserData(String token) {
        try {
            // Eliminar transacción del mapa
            userTransactions.remove(token);
            
            // Eliminar directorio del usuario
            String userDir = tempPath + token;
            File userDirFile = new File(userDir);
            
            if (userDirFile.exists()) {
                deleteDirectory(userDirFile);
                log("Datos temporales eliminados para token: " + token);
            }
        } catch (Exception e) {
            log("Error limpiando datos de usuario: " + e.getMessage());
        }
    }
    
    private void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        directory.delete();
    }
    
    public boolean isTransactionActive(String token) {
        UserTransactionState state = userTransactions.get(token);
        return state != null && state.isActive();
    }
    
    private void log(String msg) {
        if (logCallback != null) {
            logCallback.accept("[TransactionManager] " + msg);
        } else {
            System.out.println("[TransactionManager] " + msg);
        }
    }
    
    // Clase interna para manejar el estado de la transacción por usuario
    private static class UserTransactionState {
        private final String token;
        private final String username;
        private final String backupPath;
        private final int backupNumber;
        private boolean active;
        
        public UserTransactionState(String token, String username, String backupPath, int backupNumber) {
            this.token = token;
            this.username = username;
            this.backupPath = backupPath;
            this.backupNumber = backupNumber;
            this.active = true;
        }
        
        // Getters
        public String getToken() { return token; }
        public String getUsername() { return username; }
        public String getBackupPath() { return backupPath; }
        public int getBackupNumber() { return backupNumber; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }
}