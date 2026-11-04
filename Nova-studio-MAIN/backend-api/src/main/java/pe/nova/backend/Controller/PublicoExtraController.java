package pe.nova.backend.Controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pe.nova.backend.security.LoginAttemptService;
import pe.nova.backend.service.CuentaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Endpoints públicos nuevos: especialistas, mensajes de contacto y recuperación de contraseña. */
@RestController
@RequestMapping("/api/publico")
public class PublicoExtraController {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MensajeContacto(String nombre, String correo, String telefono, String mensaje, boolean aceptaPrivacidad) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecuperarPassword(String email) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RestablecerPassword(String token, String nuevaContrasenia) {}

    @Autowired private JdbcTemplate jdbc;
    @Autowired private CuentaService cuentaService;
    @Autowired private LoginAttemptService intentos;



    @PostMapping("/mensajes")
    public ResponseEntity<Map<String, Object>> mensaje(@RequestBody MensajeContacto m, HttpServletRequest req) {
        String clave = "msg:" + req.getRemoteAddr();
        long espera = intentos.segundosRestantes(clave);
        if (espera > 0) return respuesta(429, "Has enviado varios mensajes seguidos. Intenta de nuevo en " + espera + " segundos.");

        String nombre = m.nombre() == null ? "" : m.nombre().trim();
        String correo = m.correo() == null ? "" : m.correo().trim();
        String telefono = m.telefono() == null ? "" : m.telefono().trim();
        String texto = m.mensaje() == null ? "" : m.mensaje().trim();

        if (nombre.isEmpty() || nombre.length() > 100) return respuesta(400, "Ingresa tu nombre (máximo 100 caracteres).");
        if (correo.length() > 100 || !EMAIL.matcher(correo).matches()) return respuesta(400, "Ingresa un correo válido.");
        if (telefono.length() > 20) return respuesta(400, "El teléfono es demasiado largo.");
        if (texto.isEmpty() || texto.length() > 2000) return respuesta(400, "El mensaje debe tener entre 1 y 2000 caracteres.");
        if (!m.aceptaPrivacidad()) return respuesta(400, "Debes aceptar la Política de Privacidad para enviar tu mensaje.");

        jdbc.update("INSERT INTO mensajes_contacto (nombre, correo, telefono, mensaje, acepta_privacidad) VALUES (?, ?, ?, ?, TRUE)",
                nombre, correo, telefono.isEmpty() ? null : telefono, texto);
        intentos.registrarFallo(clave, 5);   // máximo 5 mensajes seguidos por IP antes de esperar
        return respuesta(200, "¡Gracias! Recibimos tu mensaje y te responderemos pronto.");
    }

    /** Siempre responde lo mismo: no revela si el correo está registrado. */
    @PostMapping("/recuperar-password")
    public ResponseEntity<Map<String, Object>> recuperar(@RequestBody RecuperarPassword p, HttpServletRequest req) {
        String clave = "rec:" + req.getRemoteAddr();
        long espera = intentos.segundosRestantes(clave);
        if (espera > 0) return respuesta(429, "Demasiadas solicitudes. Intenta de nuevo en " + espera + " segundos.");
        intentos.registrarFallo(clave, 3);
        cuentaService.solicitarRecuperacion(p.email());
        return respuesta(200, "Si el correo está registrado, recibirás un enlace para restablecer tu contraseña.");
    }

    @PostMapping("/restablecer-password")
    public ResponseEntity<Map<String, Object>> restablecer(@RequestBody RestablecerPassword p, HttpServletRequest req) {
        String clave = "rst:" + req.getRemoteAddr();
        long espera = intentos.segundosRestantes(clave);
        if (espera > 0) return respuesta(429, "Demasiados intentos. Intenta de nuevo en " + espera + " segundos.");
        String error = cuentaService.restablecer(p.token(), p.nuevaContrasenia());
        if (error != null) {
            intentos.registrarFallo(clave, 10);
            return respuesta(400, error);
        }
        return respuesta(200, "Contraseña restablecida. Ya puedes iniciar sesión.");
    }

    private static ResponseEntity<Map<String, Object>> respuesta(int estado, String mensaje) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("mensaje", mensaje);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
