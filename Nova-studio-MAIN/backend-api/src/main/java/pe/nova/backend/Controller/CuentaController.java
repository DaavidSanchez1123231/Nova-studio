package pe.nova.backend.Controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pe.nova.backend.security.LoginAttemptService;
import pe.nova.backend.service.CuentaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class CuentaController {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CambiarPassword(String contraseniaActual, String nuevaContrasenia) {}

    @Autowired private CuentaService cuentaService;
    @Autowired private LoginAttemptService intentos;
    @Value("${app.frontend-url:http://localhost:4200}") private String frontendUrl;
    @Value("${app.nombre-salon:NOVA Beauty Salon}") private String salon;

    /** Enlace del correo de bienvenida: GET /api/usuarios/verificar?token=... */
    @GetMapping(value = "/verificar", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> verificar(@RequestParam String token) {
        Map<String, Object> r = cuentaService.verificarPorToken(token);
        boolean ok = Boolean.TRUE.equals(r.get("ok"));
        String titulo = ok ? "¡Cuenta verificada!" : "No se pudo verificar la cuenta";
        String html = "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\"><title>" + HtmlUtils.htmlEscape(salon) + "</title></head>"
                + "<body style=\"font-family:sans-serif;text-align:center;padding:48px\"><h1>" + titulo + "</h1><p>"
                + HtmlUtils.htmlEscape(String.valueOf(r.get("mensaje")), "UTF-8") + "</p>"
                + "<p><a href=\"" + HtmlUtils.htmlEscape(frontendUrl) + "/login\">Ir a iniciar sesión</a></p></body></html>";
        return ResponseEntity.status(ok ? 200 : 400).contentType(MediaType.TEXT_HTML).body(html);
    }

    /** El usuario sale del token JWT, nunca del cuerpo de la petición (el cliente podría falsificarlo). */
    @PutMapping("/cambiar-password")
    public ResponseEntity<Map<String, Object>> cambiar(@RequestBody CambiarPassword body, Authentication auth) {
        String email = auth.getName();
        String clave = "pw:" + email.toLowerCase();
        long espera = intentos.segundosRestantes(clave);
        if (espera > 0) {
            return respuesta(429, "Demasiados intentos. Intenta de nuevo en " + espera + " segundos.");
        }
        String error = cuentaService.cambiarPassword(email, body.contraseniaActual(), body.nuevaContrasenia());
        if (error != null) {
            if (error.contains("actual es incorrecta")) intentos.registrarFallo(clave, 3);
            return respuesta(400, error);
        }
        intentos.registrarExito(clave);
        return respuesta(200, "Contraseña actualizada correctamente.");
    }

    private static ResponseEntity<Map<String, Object>> respuesta(int estado, String mensaje) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("mensaje", mensaje);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
