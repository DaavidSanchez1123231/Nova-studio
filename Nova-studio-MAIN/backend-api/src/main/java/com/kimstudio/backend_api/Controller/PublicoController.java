package com.kimstudio.backend_api.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/publico")
@CrossOrigin(origins = "http://localhost:4200")
public class PublicoController {

    private final JdbcTemplate jdbcTemplate;

    public PublicoController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Galería pública
    @GetMapping("/galeria")
    public ResponseEntity<List<Map<String, Object>>> obtenerGaleriaPublica() {

        String sql = """
                SELECT
                    id_imagen,
                    titulo,
                    categoria,
                    url_imagen,
                    url_imagen_despues,
                    fecha_subida
                FROM public.galeria
                WHERE estado = '1'
                ORDER BY fecha_subida DESC
                """;

        return ResponseEntity.ok(jdbcTemplate.queryForList(sql));
    }

    // Servicios públicos
    @GetMapping("/servicios")
    public ResponseEntity<List<Map<String, Object>>> obtenerServiciosPublicos() {

        String sql = """
                SELECT
                    id_servicio,
                    nombre,
                    descripcion,
                    precio,
                    duracion_minutos,
                    categoria,
                    imagen_url
                FROM public.servicios
                WHERE estado = '1'
                """;

        return ResponseEntity.ok(jdbcTemplate.queryForList(sql));
    }

    // Especialistas públicos
    @GetMapping("/especialistas")
    public ResponseEntity<List<Map<String, Object>>> obtenerEspecialistasPublicos() {

        String sql = """
                SELECT
                    id_especialista,
                    nombre,
                    especialidad
                FROM public.especialistas
                WHERE estado = TRUE
                ORDER BY nombre
                """;

        return ResponseEntity.ok(jdbcTemplate.queryForList(sql));
    }

    // Promociones públicas
    @GetMapping("/promociones")
    public ResponseEntity<List<Map<String, Object>>> obtenerPromocionesPublicas() {

        String sql = """
                SELECT
                    id_promocion,
                    nombre,
                    descuento,
                    codigo,
                    fecha_expiracion,
                    descripcion
                FROM public.promociones
                WHERE estado = TRUE
                  AND fecha_expiracion >= CURRENT_DATE
                ORDER BY id_promocion DESC
                """;

        return ResponseEntity.ok(jdbcTemplate.queryForList(sql));
    }

// Información pública del salón para la sección Contáctanos
@GetMapping("/configuracion")
public ResponseEntity<Map<String, Object>> obtenerConfiguracionPublica() {

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
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(resultado.get(0));

    } catch (Exception e) {
        System.err.println(
            "Error al consultar configuración pública: " + e.getMessage()
        );

        return ResponseEntity.internalServerError().build();
    }
}
}
