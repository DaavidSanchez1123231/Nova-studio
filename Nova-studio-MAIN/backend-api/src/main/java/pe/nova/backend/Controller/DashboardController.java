package pe.nova.backend.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "http://localhost:4200")
public class DashboardController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==========================================
    // MÉTRICAS REALES DEL DASHBOARD
    // ==========================================

    @GetMapping("/metricas")
    public ResponseEntity<Map<String, Object>> getMetricas() {

        Map<String, Object> metricas = new HashMap<>();

        // Ventas completadas del día.
        Map<String, Object> ventasHoy = jdbcTemplate.queryForMap(
            """
            SELECT
                COALESCE(SUM(monto_total), 0) AS "totalCaja",
                COALESCE(
                    SUM(CASE
                        WHEN tipo_venta = 'servicio'
                        THEN monto_total ELSE 0
                    END), 0
                ) AS "porServicios",
                COALESCE(
                    SUM(CASE
                        WHEN tipo_venta = 'producto'
                        THEN monto_total ELSE 0
                    END), 0
                ) AS "porProductos"
            FROM public.ventas
            WHERE fecha_venta::DATE = CURRENT_DATE
              AND LOWER(COALESCE(estado_venta, '')) = 'completada'
            """
        );

        metricas.put("totalCaja", ventasHoy.get("totalCaja"));
        metricas.put("porServicios", ventasHoy.get("porServicios"));
        metricas.put("porProductos", ventasHoy.get("porProductos"));

        // Se comparte con el nombre que usará la tarjeta de ingresos.
        metricas.put("ingresosHoy", ventasHoy.get("totalCaja"));

        // Citas programadas para hoy, excluyendo las canceladas.
        Long citasHoy = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM public.citas c
            INNER JOIN public.horarios h
                ON h.id_horario = c.id_horario
            WHERE h.fecha = CURRENT_DATE
              AND LOWER(COALESCE(
                    c.estado, 'pendiente_confirmacion'
                  )) NOT IN (
                    'cancelada', 'cancelado', 'cancelled'
                  )
            """,
            Long.class
        );

        metricas.put("citasHoy", citasHoy == null ? 0L : citasHoy);

        // Clientes registrados.
        Long clientes = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM public.usuarios
            WHERE LOWER(COALESCE(rol, '')) = 'cliente'
            """,
            Long.class
        );

        metricas.put(
            "clientesRegistrados",
            clientes == null ? 0L : clientes
        );

        // Solicitudes de cita que todavía esperan confirmación.
        Long pendientes = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM public.citas
            WHERE LOWER(COALESCE(
                estado, 'pendiente_confirmacion'
            )) IN (
                'pendiente',
                'pendiente_confirmacion'
            )
            """,
            Long.class
        );

        metricas.put(
            "solicitudesPendientes",
            pendientes == null ? 0L : pendientes
        );

        return ResponseEntity.ok(metricas);
    }

    // ==========================================
    // ÚLTIMAS CINCO CITAS REGISTRADAS
    // ==========================================

    @GetMapping("/citas-recientes")
    public ResponseEntity<List<Map<String, Object>>> getCitasRecientes() {

        String sql = """
            SELECT
                c.id_cita,
                TO_CHAR(h.fecha, 'DD/MM/YYYY') AS fecha,
                TO_CHAR(h.hora_inicio, 'HH24:MI') AS hora,
                u.nombre AS nombre_cliente,
                s.nombre AS nombre_servicio,
                CASE LOWER(COALESCE(
                    c.estado, 'pendiente_confirmacion'
                ))
                    WHEN 'pendiente_confirmacion' THEN 'Pendiente'
                    WHEN 'pendiente' THEN 'Pendiente'
                    WHEN 'confirmada' THEN 'Confirmada'
                    WHEN 'confirmado' THEN 'Confirmada'
                    WHEN 'completada' THEN 'Completada'
                    WHEN 'completado' THEN 'Completada'
                    WHEN 'cancelada' THEN 'Cancelada'
                    WHEN 'cancelado' THEN 'Cancelada'
                    WHEN 'cancelled' THEN 'Cancelada'
                    ELSE COALESCE(c.estado, 'Pendiente')
                END AS estado
            FROM public.citas c
            INNER JOIN public.usuarios u
                ON u.id_usuario = c.id_usuario
            INNER JOIN public.horarios h
                ON h.id_horario = c.id_horario
            INNER JOIN public.servicios s
                ON s.id_servicio = c.id_servicio
            ORDER BY c.id_cita DESC
            LIMIT 5
            """;

        List<Map<String, Object>> citas =
            jdbcTemplate.queryForList(sql);

        return ResponseEntity.ok(citas);
    }
}