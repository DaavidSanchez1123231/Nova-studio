
package com.kimstudio.backend_api.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/configuracion")
@CrossOrigin(origins = "http://localhost:4200")
public class ConfiguracionController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Pattern EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    // Consultar información de NOVA
    @GetMapping("/empresa")
    public ResponseEntity<Map<String, Object>> obtenerConfiguracion() {

        String sql = """
            SELECT
                nombre_salon,
                telefono_whatsapp,
                correo_contacto,
                direccion,
                instagram,
                facebook,
                tiktok_url,
                whatsapp_url
            FROM public.configuracion
            ORDER BY id_config DESC
            LIMIT 1
            """;

        try {
            List<Map<String, Object>> resultado =
                    jdbcTemplate.queryForList(sql);

            if (resultado.isEmpty()) {
                return respuesta(404, "No existe configuración del salón.");
            }

            return ResponseEntity.ok(resultado.get(0));

        } catch (Exception e) {
            return respuesta(500, "No se pudo consultar la configuración.");
        }
    }

    // Actualizar información de NOVA
    @PutMapping("/empresa")
    public ResponseEntity<Map<String, Object>> actualizarConfiguracion(
            @RequestBody Map<String, Object> body) {

        String nombre = valor(body, "nombre_salon");
        String telefono = valor(body, "telefono_whatsapp");
        String correo = valor(body, "correo_contacto");
        String direccion = valor(body, "direccion");
        String instagram = valor(body, "instagram");
        String facebook = valor(body, "facebook");
        String tiktokUrl = valor(body, "tiktok_url");
        String whatsappUrl = valor(body, "whatsapp_url");

        if (nombre.isBlank() || nombre.length() > 100) {
            return respuesta(400, "El nombre del salón es obligatorio.");
        }

        if (telefono.isBlank() || telefono.length() > 20) {
            return respuesta(400, "Ingresa un teléfono válido.");
        }

        if (!EMAIL.matcher(correo).matches() || correo.length() > 150) {
            return respuesta(400, "Ingresa un correo válido.");
        }

        if (direccion.isBlank() || direccion.length() > 200) {
            return respuesta(400, "La dirección es obligatoria.");
        }

        if (instagram.length() > 200 || facebook.length() > 200) {
            return respuesta(400, "Revisa los enlaces de redes sociales.");
        }

        if (!esUrlValida(tiktokUrl) || !esUrlValida(whatsappUrl)) {
            return respuesta(400, "Los enlaces de TikTok y WhatsApp deben ser URLs válidas.");
        }

        String sql = """
            UPDATE public.configuracion
            SET
                nombre_salon = ?,
                telefono_whatsapp = ?,
                correo_contacto = ?,
                direccion = ?,
                instagram = ?,
                facebook = ?,
                tiktok_url = ?,
                whatsapp_url = ?,
                fecha_actualizacion = NOW()
            WHERE id_config = (
                SELECT id_config
                FROM public.configuracion
                ORDER BY id_config DESC
                LIMIT 1
            )
            """;

        try {
            int filas = jdbcTemplate.update(
                    sql,
                    nombre,
                    telefono,
                    correo,
                    direccion,
                    instagram,
                    facebook,
                    tiktokUrl,
                    whatsappUrl
            );

            if (filas == 0) {
                return respuesta(
                        404,
                        "No existe una configuración que actualizar."
                );
            }

            return respuesta(
                    200,
                    "Información de NOVA actualizada correctamente."
            );

        } catch (Exception e) {
            return respuesta(
                    500,
                    "No se pudo actualizar la información del salón."
            );
        }
    }

    // Validar enlaces HTTP y HTTPS. Se permiten campos vacíos.
    private boolean esUrlValida(String valor) {

        if (valor == null || valor.isBlank()) {
            return true;
        }

        if (valor.length() > 250) {
            return false;
        }

        try {
            URI uri = URI.create(valor);

            String esquema = uri.getScheme();

            return ("https".equalsIgnoreCase(esquema)
                    || "http".equalsIgnoreCase(esquema))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();

        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String valor(Map<String, Object> body, String campo) {
        Object dato = body.get(campo);
        return dato == null ? "" : dato.toString().trim();
    }

    private ResponseEntity<Map<String, Object>> respuesta(
            int estado,
            String mensaje) {

        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("mensaje", mensaje);

        return ResponseEntity.status(estado).body(cuerpo);
    }
}
