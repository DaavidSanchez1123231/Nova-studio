package pe.nova.backend.Controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registra el error real en el log y devuelve un mensaje genérico para el cliente. */
final class Errores {
    private static final Logger log = LoggerFactory.getLogger("backend-api");

    private Errores() {}

    static String generico(Exception e) {
        log.error("Error no controlado", e);
        return "Ocurrió un error. Intenta nuevamente.";
    }
}
