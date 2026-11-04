package pe.nova.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;

/** Ninguna excepción no controlada devuelve detalles internos al cliente. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> datosInvalidos(DataIntegrityViolationException e) {
        log.warn("Violación de integridad de datos", e);
        return ResponseEntity.status(409).body(Map.of("mensaje", "Los datos enviados no son válidos o ya existen."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> errorGeneral(Exception e) {
        log.error("Error no controlado", e);
        return ResponseEntity.status(500).body(Map.of("mensaje", "Ocurrió un error. Intenta nuevamente."));
    }
}
