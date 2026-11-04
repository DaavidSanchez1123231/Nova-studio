package pe.nova.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Verificación de cuenta, consentimiento, recuperación y cambio de contraseña. */
@Service
public class CuentaService {

    public static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 72;      // límite de BCrypt

    private final SecureRandom random = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final EmailService emailService;
    private final String frontendUrl;

    public CuentaService(JdbcTemplate jdbc, PasswordEncoder encoder, EmailService emailService,
                         @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.emailService = emailService;
        this.frontendUrl = frontendUrl;
    }

    /** null si la contraseña es válida; si no, el mensaje de error. */
    public String validarPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            return "La contraseña debe tener al menos " + MIN_PASSWORD + " caracteres.";
        }
        if (password.length() > MAX_PASSWORD) {
            return "La contraseña no puede superar " + MAX_PASSWORD + " caracteres.";
        }
        return null;
    }

    public Map<String, Object> verificarPorToken(String token) {
        Map<String, Object> r = new HashMap<>();
        UUID uuid;
        try {
            uuid = UUID.fromString(token == null ? "" : token.trim());
        } catch (IllegalArgumentException e) {
            r.put("ok", false);
            r.put("mensaje", "Enlace de verificación inválido.");
            return r;
        }
        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM sp_verificar_cuenta(?)", uuid);
        String mensaje = String.valueOf(fila.get("mensaje"));
        r.put("ok", mensaje.toLowerCase().contains("éxito"));
        r.put("mensaje", mensaje);
        return r;
    }

    public void guardarConsentimiento(String email, boolean privacidad, boolean terminos, boolean marketing, String version) {
        jdbc.update("UPDATE usuarios SET acepta_privacidad = ?, acepta_terminos = ?, acepta_marketing = ?, "
                + "fecha_consentimiento = NOW(), version_politicas = ? WHERE LOWER(email) = LOWER(?)",
                privacidad, terminos, marketing, version, email);
    }

    /** No revela si el correo existe: el que llama siempre responde lo mismo. */
    public void solicitarRecuperacion(String email) {
        if (email == null || email.isBlank()) return;
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT id_usuario, nombre FROM usuarios WHERE LOWER(email) = LOWER(?) AND cuenta_verificada = TRUE",
                email.trim());
        if (filas.isEmpty()) return;

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        // En la base solo se guarda el hash del token: si se filtra la tabla, los enlaces no sirven
        jdbc.update("UPDATE usuarios SET token_recuperacion_hash = ?, "
                + "recuperacion_expira = NOW() + INTERVAL '30 minutes' WHERE id_usuario = ?",   // vence en 30 min (igual que el correo)
                sha256(token), filas.get(0).get("id_usuario"));

        emailService.enviarCorreoRecuperacion(email.trim(), String.valueOf(filas.get(0).get("nombre")),
                frontendUrl + "/restablecer?token=" + token);
    }

    /** null si se cambió; si no, el mensaje de error. */
    public String restablecer(String token, String nueva) {
        String error = validarPassword(nueva);
        if (error != null) return error;
        if (token == null || token.isBlank()) return "El enlace es inválido o ya venció. Solicita uno nuevo.";
        int filas = jdbc.update("UPDATE usuarios SET contraseña = ?, token_recuperacion_hash = NULL, "
                + "recuperacion_expira = NULL WHERE token_recuperacion_hash = ? AND recuperacion_expira > NOW()",
                encoder.encode(nueva), sha256(token.trim()));
        return filas > 0 ? null : "El enlace es inválido o ya venció. Solicita uno nuevo.";
    }

    /** null si se cambió; si no, el mensaje de error. */
    public String cambiarPassword(String email, String actual, String nueva) {
        String error = validarPassword(nueva);
        if (error != null) return error;
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT contraseña FROM usuarios WHERE LOWER(email) = LOWER(?)", email);
        if (filas.isEmpty()) return "Usuario no encontrado.";
        String hash = String.valueOf(filas.get(0).get("contraseña"));
        if (actual == null || !encoder.matches(actual, hash)) return "La contraseña actual es incorrecta.";
        if (encoder.matches(nueva, hash)) return "La nueva contraseña debe ser distinta de la actual.";
        jdbc.update("UPDATE usuarios SET contraseña = ? WHERE LOWER(email) = LOWER(?)", encoder.encode(nueva), email);
        return null;
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
