package pe.nova.backend.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

@Service
public class LoginAttemptService {

    public static final int GRATIS_POR_CUENTA = 3;  
    public static final int GRATIS_POR_IP = 20;     
    private static final long BASE_SEG = 2;
    private static final long MAX_SEG = 15 * 60;
    private static final long OLVIDAR_MS = 30 * 60 * 1000L; 
    private static final int MAX_CLAVES = 10_000;

    private record Estado(int fallos, long bloqueadoHastaMs, long ultimoFalloMs) {}

    private final Map<String, Estado> estados = new ConcurrentHashMap<>();
    private final LongSupplier reloj;

    public LoginAttemptService() { this(System::currentTimeMillis); }
    LoginAttemptService(LongSupplier reloj) { this.reloj = reloj; }

    /** Segundos que faltan para poder intentar de nuevo; 0 si puede intentar ya. */
    public long segundosRestantes(String clave) {
        Estado e = estados.get(clave);
        if (e == null) return 0;
        long ahora = reloj.getAsLong();
        if (ahora - e.ultimoFalloMs() > OLVIDAR_MS) { estados.remove(clave); return 0; }
        long ms = e.bloqueadoHastaMs() - ahora;
        return ms <= 0 ? 0 : (ms + 999) / 1000;
    }

    public void registrarFallo(String clave, int intentosGratis) {
        long ahora = reloj.getAsLong();
        if (estados.size() > MAX_CLAVES) limpiar(ahora);
        estados.merge(clave, new Estado(1, 0, ahora), (viejo, nuevo) -> {
            int fallos = (ahora - viejo.ultimoFalloMs() > OLVIDAR_MS) ? 1 : viejo.fallos() + 1;
            long espera = 0;
            if (fallos > intentosGratis) {
                int exp = Math.min(fallos - intentosGratis - 1, 20);
                espera = Math.min(MAX_SEG, BASE_SEG << exp);
            }
            return new Estado(fallos, espera == 0 ? 0 : ahora + espera * 1000, ahora);
        });
        // Si es el primer fallo y no hay intentos gratis, aplica espera de inmediato
        if (intentosGratis < 1) estados.computeIfPresent(clave,
                (k, e) -> new Estado(e.fallos(), ahora + BASE_SEG * 1000, ahora));
    }

    public void registrarExito(String clave) { estados.remove(clave); }

    private void limpiar(long ahora) {
        estados.entrySet().removeIf(e -> ahora - e.getValue().ultimoFalloMs() > OLVIDAR_MS);
    }
}
