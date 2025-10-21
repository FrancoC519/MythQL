package mythqlserver;

import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class LockManager {
    // Mapa concurrente de tabla → Lock
    private static final ConcurrentHashMap<String, ReentrantLock> tablaLocks = new ConcurrentHashMap<>();

    // Obtener o crear un lock para una tabla
    private static ReentrantLock getLock(String nombreTabla) {
        return tablaLocks.computeIfAbsent(nombreTabla.toUpperCase(), k -> new ReentrantLock());
    }

    public static void bloquearTabla(String tabla) {
        ReentrantLock lock = getLock(tabla);
        lock.lock();
    }

    public static void desbloquearTabla(String tabla) {
        ReentrantLock lock = tablaLocks.get(tabla.toUpperCase());
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    // Para bases completas (opcional)
    public static void bloquearBase(String base) {
        bloquearTabla("DB_" + base);
    }

    public static void desbloquearBase(String base) {
        desbloquearTabla("DB_" + base);
    }
}
