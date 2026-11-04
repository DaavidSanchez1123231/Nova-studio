package pe.nova.backend.Controller;

import pe.nova.backend.model.RegistroDTO;
import pe.nova.backend.model.loginDTO;
import pe.nova.backend.security.LoginAttemptService;
import pe.nova.backend.service.CuentaService;
import pe.nova.backend.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> ROLES = Set.of("cliente", "recepcion", "admin");
    /** Versión de los textos legales aceptados; súbela cuando cambies las políticas. */
    private static final String VERSION_POLITICAS = "2026-10";

    @Autowired private UsuarioService usuarioService;
    @Autowired private CuentaService cuentaService;
    @Autowired private LoginAttemptService intentos;

    @PostMapping("/registro")
    public ResponseEntity<Map<String, Object>> registrar(@RequestBody RegistroDTO p) {
        String nombre = p.getNombre() == null ? "" : p.getNombre().trim();
        String email = p.getEmail() == null ? "" : p.getEmail().trim();
        String telefono = p.getTelefono() == null ? "" : p.getTelefono().trim();

        if (nombre.isEmpty() || nombre.length() > 50) return error(400, "Ingresa un nombre válido (máximo 50 caracteres).");
        if (email.length() > 50 || !EMAIL.matcher(email).matches()) return error(400, "Ingresa un correo válido.");
        String errPass = cuentaService.validarPassword(p.getPassword());
        if (errPass != null) return error(400, errPass);
        if (!telefono.isEmpty() && !telefono.matches("\\d{6,9}")) return error(400, "El teléfono debe tener entre 6 y 9 dígitos.");
        if (!p.isAceptaPrivacidad() || !p.isAceptaTerminos()) {
            return error(400, "Debes aceptar la Política de Privacidad y los Términos y Condiciones para registrarte.");
        }

        Map<String, Object> resultado = usuarioService.registrarUsuario(nombre, email, telefono.isEmpty() ? null : telefono, p.getPassword());
        // Un registro exitoso trae el token de verificación: se guarda el consentimiento y NUNCA se devuelve al cliente
        if (resultado.get("token_generado") != null) {
            cuentaService.guardarConsentimiento(email, true, true, p.isAceptaMarketing(), VERSION_POLITICAS);
        }
        Map<String, Object> salida = new HashMap<>();
        salida.put("mensaje", resultado.get("mensaje"));
        return ResponseEntity.ok(salida);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody loginDTO p, HttpServletRequest req) {
        String email = p.getEmail() == null ? "" : p.getEmail().trim().toLowerCase();
        String claveCuenta = "u:" + email;
        String claveIp = "ip:" + req.getRemoteAddr();

        long espera = Math.max(intentos.segundosRestantes(claveCuenta), intentos.segundosRestantes(claveIp));
        if (espera > 0) {
            Map<String, Object> cuerpo = new HashMap<>();
            cuerpo.put("mensaje", "Demasiados intentos. Intenta de nuevo en " + espera + " segundos.");
            cuerpo.put("reintentarEn", espera);
            return ResponseEntity.status(429).header("Retry-After", String.valueOf(espera)).body(cuerpo);
        }

        Map<String, Object> respuesta = usuarioService.login(p.getEmail(), p.getPassword());
        if (respuesta.containsKey("token")) {
            intentos.registrarExito(claveCuenta);
            return ResponseEntity.ok(respuesta);
        }
        intentos.registrarFallo(claveCuenta, LoginAttemptService.GRATIS_POR_CUENTA);
        intentos.registrarFallo(claveIp, LoginAttemptService.GRATIS_POR_IP);
        return ResponseEntity.status(401).body(respuesta);
    }

    /** Solo administradores (regla en SecurityConfig). Valida que el rol exista. */
    @PutMapping("/rol/{idUsuario}")
    public ResponseEntity<Map<String, Object>> cambiarRol(@PathVariable int idUsuario, @RequestBody Map<String, String> body) {
        String nuevoRol = body.get("rol") == null ? "" : body.get("rol").trim().toLowerCase();
        if (!ROLES.contains(nuevoRol)) return error(400, "Rol no válido. Usa: cliente, recepcion o admin.");

        int filas = usuarioService.actualizarRolPrueba(idUsuario, nuevoRol);
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("mensaje", filas > 0 ? "Rol actualizado a: " + nuevoRol : "Usuario no encontrado");
        if (filas > 0) respuesta.put("idUsuario", idUsuario);
        return ResponseEntity.ok(respuesta);
    }

    private static ResponseEntity<Map<String, Object>> error(int estado, String mensaje) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("mensaje", mensaje);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
