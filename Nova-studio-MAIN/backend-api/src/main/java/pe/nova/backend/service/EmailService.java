package pe.nova.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final JdbcTemplate jdbc;

    @Value("${spring.mail.username:}")
    private String remitente;
    @Value("${app.backend-url:http://localhost:8081}")
    private String backendUrl;
    @Value("${app.nombre-salon:NOVA Beauty Salon}")
    private String salon;

    public EmailService(JavaMailSender mailSender, JdbcTemplate jdbc) {
        this.mailSender = mailSender;
        this.jdbc = jdbc;
    }
    @Async
    public void enviarCorreoVerificacion(String destino, String nombre) {
        try {
            List<Map<String, Object>> filas = jdbc.queryForList(
                    "SELECT token_verificacion FROM usuarios WHERE LOWER(email) = LOWER(?) AND cuenta_verificada = FALSE", destino);
            if (filas.isEmpty() || filas.get(0).get("token_verificacion") == null) {
                log.warn("No hay token de verificación pendiente para el correo indicado.");
                return;
            }
            String enlace = backendUrl + "/api/usuarios/verificar?token=" + filas.get(0).get("token_verificacion");
            enviar(destino, "¡Bienvenido/a a " + salon + "! Verifica tu cuenta",
                    "Hola " + nombre + ",\n\nGracias por registrarte. Para verificar tu cuenta haz clic aquí:\n\n"
                            + enlace + "\n\n¡Te esperamos!");
        } catch (Exception e) {
            log.error("No se pudo enviar el correo de verificación", e);
        }
    }

    @Async
    public void enviarCorreoRecuperacion(String destino, String nombre, String enlace) {
        try {
            enviar(destino, "Recupera tu contraseña - " + salon,
                    "Hola " + nombre + ",\n\nRecibimos una solicitud para restablecer tu contraseña. "
                            + "Usa este enlace (vence en 30 minutos):\n\n" + enlace
                            + "\n\nSi no fuiste tú, ignora este mensaje: tu contraseña no cambiará.");
        } catch (Exception e) {
            log.error("No se pudo enviar el correo de recuperación", e);
        }
    }

    private void enviar(String destino, String asunto, String texto) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(destino);
        mensaje.setSubject(asunto);
        mensaje.setText(texto);
        mailSender.send(mensaje);
        log.info("Correo enviado");
    }
}
