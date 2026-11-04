
package pe.nova.backend.Controller;

import pe.nova.backend.model.CitaRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Locale;

@RestController
@RequestMapping("/api/citas")
@CrossOrigin(origins = "http://localhost:4200")
public class CitaController {

    private final JdbcTemplate jdbcTemplate;

    public CitaController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==========================================
    // LISTAR CITAS
    // ==========================================

    @GetMapping
    public List<Map<String, Object>> obtenerCitas() {

        String sql =
            "SELECT c.id_cita, " +
            "c.id_servicio, " +
            "c.id_horario, " +
            "h.fecha, " +
            "TO_CHAR(h.hora_inicio, 'HH24:MI') AS hora, " +
            "u.nombre AS cliente, " +
            "s.nombre AS servicio, " +
            "CASE LOWER(COALESCE(c.estado, 'pendiente_confirmacion')) " +
            "WHEN 'pendiente_confirmacion' THEN 'Pendiente' " +
            "WHEN 'pendiente' THEN 'Pendiente' " +
            "WHEN 'confirmada' THEN 'Confirmada' " +
            "WHEN 'confirmado' THEN 'Confirmada' " +
            "WHEN 'completada' THEN 'Completada' " +
            "WHEN 'completado' THEN 'Completada' " +
            "WHEN 'cancelada' THEN 'Cancelada' " +
            "WHEN 'cancelado' THEN 'Cancelada' " +
            "WHEN 'cancelled' THEN 'Cancelada' " +
            "ELSE c.estado END AS estado " +
            "FROM citas c " +
            "INNER JOIN usuarios u ON c.id_usuario = u.id_usuario " +
            "INNER JOIN horarios h ON c.id_horario = h.id_horario " +
            "INNER JOIN servicios s ON c.id_servicio = s.id_servicio " +
            "ORDER BY h.fecha DESC, h.hora_inicio DESC";

        return jdbcTemplate.queryForList(sql);
    }

    // ==========================================
    // REGISTRAR CITA
    // ==========================================

    @PostMapping
    @Transactional
    public ResponseEntity<?> crearCita(
        @RequestBody CitaRequest request
    ) {

        if (request == null ||
            request.getId_usuario() == null ||
            request.getId_servicio() == null ||
            request.getId_horario() == null ||
            request.getId_usuario() <= 0 ||
            request.getId_servicio() <= 0 ||
            request.getId_horario() <= 0) {

            return ResponseEntity.badRequest().body(
                Map.of(
                    "error",
                    "Debes seleccionar un cliente, un servicio y un horario."
                )
            );
        }

        Integer clienteExiste = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM usuarios " +
            "WHERE id_usuario = ? AND LOWER(rol) = 'cliente'",
            Integer.class,
            request.getId_usuario()
        );

        if (clienteExiste == null || clienteExiste == 0) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "El cliente seleccionado no existe.")
            );
        }

        // En la base de datos la columna del servicio se llama estado.
        Integer servicioExiste = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM servicios " +
            "WHERE id_servicio = ? AND estado = TRUE",
            Integer.class,
            request.getId_servicio()
        );

        if (servicioExiste == null || servicioExiste == 0) {
            return ResponseEntity.badRequest().body(
                Map.of(
                    "error",
                    "El servicio seleccionado no existe o está inactivo."
                )
            );
        }

        List<Map<String, Object>> horarios =
            jdbcTemplate.queryForList(
                "SELECT id_horario, id_especialista " +
                "FROM horarios " +
                "WHERE id_horario = ? " +
                "AND estado = 'DISPONIBLE' " +
                "AND fecha >= CURRENT_DATE " +
                "FOR UPDATE",
                request.getId_horario()
            );

        if (horarios.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                Map.of(
                    "error",
                    "El horario seleccionado ya no está disponible."
                )
            );
        }

        Object especialistaObjeto =
            horarios.get(0).get("id_especialista");

        if (!(especialistaObjeto instanceof Number)) {
            return ResponseEntity.badRequest().body(
                Map.of(
                    "error",
                    "El horario no tiene un especialista asignado."
                )
            );
        }

        int idEspecialista =
            ((Number) especialistaObjeto).intValue();

        int filasActualizadas = jdbcTemplate.update(
            "UPDATE horarios " +
            "SET estado = 'RESERVADO', fecha_bloqueo = NULL " +
            "WHERE id_horario = ? AND estado = 'DISPONIBLE'",
            request.getId_horario()
        );

        if (filasActualizadas == 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                Map.of(
                    "error",
                    "El horario acaba de ser reservado. Selecciona otro."
                )
            );
        }

        jdbcTemplate.update(
            "INSERT INTO citas " +
            "(id_usuario, id_especialista, id_servicio, id_horario) " +
            "VALUES (?, ?, ?, ?)",
            request.getId_usuario(),
            idEspecialista,
            request.getId_servicio(),
            request.getId_horario()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
            Map.of("mensaje", "Cita registrada correctamente.")
        );
    }

    // ==========================================
    // MODIFICAR CITA
    // ==========================================

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> modificarCita(
        @PathVariable Integer id,
        @RequestBody Map<String, Object> datos
    ) {

        if (id == null || id <= 0 || datos == null) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "Los datos de la cita no son válidos.")
            );
        }

        Object servicioObjeto = datos.get("id_servicio");
        Object horarioObjeto = datos.get("id_horario");
        Object estadoObjeto = datos.get("estado");

        if (!(servicioObjeto instanceof Number) ||
            !(horarioObjeto instanceof Number) ||
            !(estadoObjeto instanceof String)) {

            return ResponseEntity.badRequest().body(
                Map.of(
                    "error",
                    "Debes enviar el servicio, el horario y el estado."
                )
            );
        }

        int idServicio =
            ((Number) servicioObjeto).intValue();

        int idHorarioNuevo =
            ((Number) horarioObjeto).intValue();

        if (idServicio <= 0 || idHorarioNuevo <= 0) {
            return ResponseEntity.badRequest().body(
                Map.of(
                    "error",
                    "El servicio o el horario seleccionado no son válidos."
                )
            );
        }

        String estadoEntrada =
            ((String) estadoObjeto).trim().toLowerCase(Locale.ROOT);

        // Normalizar el estado recibido desde Angular al formato
        // utilizado por la base de datos.
        String estadoBD;

        switch (estadoEntrada) {
            case "pendiente":
            case "pendiente_confirmacion":
                estadoBD = "pendiente_confirmacion";
                break;

            case "confirmada":
            case "confirmado":
                estadoBD = "confirmada";
                break;

            case "completada":
            case "completado":
                estadoBD = "completada";
                break;

            case "cancelada":
            case "cancelado":
            case "cancelled":
                estadoBD = "cancelada";
                break;

            default:
                return ResponseEntity.badRequest().body(
                    Map.of("error", "El estado seleccionado no es válido.")
                );
        }

        // Bloquear la cita para evitar modificaciones simultáneas.
        List<Map<String, Object>> citasActuales =
            jdbcTemplate.queryForList(
                "SELECT id_cita, id_horario, id_especialista, estado " +
                "FROM citas WHERE id_cita = ? FOR UPDATE",
                id
            );

        if (citasActuales.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                Map.of("error", "La cita que deseas modificar no existe.")
            );
        }

        Map<String, Object> citaActual = citasActuales.get(0);

        Object horarioAnteriorObjeto = citaActual.get("id_horario");
        Integer idHorarioAnterior = null;

        if (horarioAnteriorObjeto instanceof Number) {
            idHorarioAnterior =
                ((Number) horarioAnteriorObjeto).intValue();
        }

        boolean citaYaCancelada =
            esEstadoCancelado(citaActual.get("estado"));

        // ------------------------------------------
        // CASO 1: CANCELAR CITA
        // ------------------------------------------
        // Se procesa antes de validar el servicio.
        // Una cita debe poder cancelarse aunque el servicio
        // esté inactivo o ya no aparezca en el catálogo.

        if ("cancelada".equals(estadoBD)) {

            jdbcTemplate.update(
                "UPDATE citas SET estado = 'cancelada' " +
                "WHERE id_cita = ?",
                id
            );

            // Liberar el horario solo si esta cita no estaba
            // cancelada previamente y el horario sigue reservado.
            if (!citaYaCancelada && idHorarioAnterior != null) {

                jdbcTemplate.update(
                    "UPDATE horarios " +
                    "SET estado = 'DISPONIBLE', fecha_bloqueo = NULL " +
                    "WHERE id_horario = ? AND estado = 'RESERVADO'",
                    idHorarioAnterior
                );
            }

            return ResponseEntity.ok(
                Map.of("mensaje", "La cita fue cancelada correctamente.")
            );
        }

        // ------------------------------------------
        // CASO 2: VALIDAR SERVICIO ACTIVO
        // ------------------------------------------

        Integer servicioExiste = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM servicios " +
            "WHERE id_servicio = ? AND estado = TRUE",
            Integer.class,
            idServicio
        );

        if (servicioExiste == null || servicioExiste == 0) {
            return ResponseEntity.badRequest().body(
                Map.of(
                    "error",
                    "El servicio seleccionado no existe o está inactivo."
                )
            );
        }

        Integer idEspecialista;

        // ------------------------------------------
        // CASO 3: MANTENER EL MISMO HORARIO
        // ------------------------------------------

        if (idHorarioAnterior != null &&
            idHorarioNuevo == idHorarioAnterior) {

            if (citaYaCancelada) {

                // Una cita cancelada que vuelve a activarse debe
                // reservar nuevamente su horario si sigue libre.
                List<Map<String, Object>> horarioDisponible =
                    jdbcTemplate.queryForList(
                        "SELECT id_especialista FROM horarios " +
                        "WHERE id_horario = ? " +
                        "AND estado = 'DISPONIBLE' " +
                        "AND fecha >= CURRENT_DATE " +
                        "FOR UPDATE",
                        idHorarioNuevo
                    );

                if (horarioDisponible.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        Map.of(
                            "error",
                            "El horario anterior ya no está disponible. " +
                            "Selecciona otro horario."
                        )
                    );
                }

                Object especialistaObjeto =
                    horarioDisponible.get(0).get("id_especialista");

                if (!(especialistaObjeto instanceof Number)) {
                    return ResponseEntity.badRequest().body(
                        Map.of(
                            "error",
                            "El horario no tiene un especialista asignado."
                        )
                    );
                }

                idEspecialista =
                    ((Number) especialistaObjeto).intValue();

                int actualizados = jdbcTemplate.update(
                    "UPDATE horarios " +
                    "SET estado = 'RESERVADO', fecha_bloqueo = NULL " +
                    "WHERE id_horario = ? AND estado = 'DISPONIBLE'",
                    idHorarioNuevo
                );

                if (actualizados == 0) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        Map.of(
                            "error",
                            "El horario acaba de ser reservado. Selecciona otro."
                        )
                    );
                }

            } else {

                // La cita sigue activa y conserva su horario reservado.
                List<Map<String, Object>> horarioActual =
                    jdbcTemplate.queryForList(
                        "SELECT id_especialista FROM horarios " +
                        "WHERE id_horario = ? " +
                        "AND estado = 'RESERVADO' " +
                        "FOR UPDATE",
                        idHorarioAnterior
                    );

                if (horarioActual.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        Map.of(
                            "error",
                            "El horario actual ya no está reservado. " +
                            "Selecciona un horario disponible."
                        )
                    );
                }

                Object especialistaObjeto =
                    horarioActual.get(0).get("id_especialista");

                if (!(especialistaObjeto instanceof Number)) {
                    return ResponseEntity.badRequest().body(
                        Map.of(
                            "error",
                            "El horario no tiene un especialista asignado."
                        )
                    );
                }

                idEspecialista =
                    ((Number) especialistaObjeto).intValue();
            }

        } else {

            // ------------------------------------------
            // CASO 4: CAMBIAR A OTRO HORARIO
            // ------------------------------------------

            List<Map<String, Object>> horarioNuevo =
                jdbcTemplate.queryForList(
                    "SELECT id_especialista FROM horarios " +
                    "WHERE id_horario = ? " +
                    "AND estado = 'DISPONIBLE' " +
                    "AND fecha >= CURRENT_DATE " +
                    "FOR UPDATE",
                    idHorarioNuevo
                );

            if (horarioNuevo.isEmpty()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of(
                        "error",
                        "El nuevo horario ya no está disponible. " +
                        "Selecciona otra fecha u hora."
                    )
                );
            }

            Object especialistaObjeto =
                horarioNuevo.get(0).get("id_especialista");

            if (!(especialistaObjeto instanceof Number)) {
                return ResponseEntity.badRequest().body(
                    Map.of(
                        "error",
                        "El nuevo horario no tiene un especialista asignado."
                    )
                );
            }

            idEspecialista =
                ((Number) especialistaObjeto).intValue();

            int actualizados = jdbcTemplate.update(
                "UPDATE horarios " +
                "SET estado = 'RESERVADO', fecha_bloqueo = NULL " +
                "WHERE id_horario = ? AND estado = 'DISPONIBLE'",
                idHorarioNuevo
            );

            if (actualizados == 0) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of(
                        "error",
                        "El nuevo horario acaba de ser reservado. " +
                        "Selecciona otro."
                    )
                );
            }
        }

        // ------------------------------------------
        // GUARDAR CAMBIOS EN LA CITA
        // ------------------------------------------

        jdbcTemplate.update(
            "UPDATE citas " +
            "SET id_servicio = ?, " +
            "id_horario = ?, " +
            "id_especialista = ?, " +
            "estado = ? " +
            "WHERE id_cita = ?",
            idServicio,
            idHorarioNuevo,
            idEspecialista,
            estadoBD,
            id
        );

        // Liberar el horario anterior únicamente cuando la cita
        // lo tenía reservado y se cambió a otro horario.
        // Si la cita estaba cancelada, no se libera nuevamente:
        // ese horario podría pertenecer a otra reserva.
        if (!citaYaCancelada &&
            idHorarioAnterior != null &&
            idHorarioNuevo != idHorarioAnterior) {

            jdbcTemplate.update(
                "UPDATE horarios " +
                "SET estado = 'DISPONIBLE', fecha_bloqueo = NULL " +
                "WHERE id_horario = ? AND estado = 'RESERVADO'",
                idHorarioAnterior
            );
        }

        return ResponseEntity.ok(
            Map.of("mensaje", "Cita modificada correctamente.")
        );
    }

    // ==========================================
    // ELIMINAR CITA Y LIBERAR SU HORARIO
    // ==========================================

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> eliminarCita(
        @PathVariable Integer id
    ) {

        List<Map<String, Object>> citas =
            jdbcTemplate.queryForList(
                "SELECT id_horario, estado " +
                "FROM citas WHERE id_cita = ? FOR UPDATE",
                id
            );

        if (citas.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                Map.of("error", "No se encontró la cita.")
            );
        }

        Map<String, Object> cita = citas.get(0);

        Object horarioObjeto = cita.get("id_horario");
        Integer idHorario = null;

        if (horarioObjeto instanceof Number) {
            idHorario = ((Number) horarioObjeto).intValue();
        }

        boolean yaCancelada = esEstadoCancelado(cita.get("estado"));

        jdbcTemplate.update(
            "DELETE FROM citas WHERE id_cita = ?",
            id
        );

        // No liberar otra vez el horario de una cita que ya estaba
        // cancelada, pues podría estar siendo usado por otra cita.
        if (!yaCancelada && idHorario != null) {
            jdbcTemplate.update(
                "UPDATE horarios " +
                "SET estado = 'DISPONIBLE', fecha_bloqueo = NULL " +
                "WHERE id_horario = ? AND estado = 'RESERVADO'",
                idHorario
            );
        }

        return ResponseEntity.ok(
            Map.of(
                "mensaje",
                "Cita eliminada correctamente."
            )
        );
    }

    // ==========================================
    // COMPROBAR SI LA CITA YA ESTABA CANCELADA
    // ==========================================

    private boolean esEstadoCancelado(Object estadoObjeto) {

        if (estadoObjeto == null) {
            return false;
        }

        String estado = estadoObjeto
            .toString()
            .trim()
            .toLowerCase(Locale.ROOT);

        return estado.equals("cancelada") ||
               estado.equals("cancelado") ||
               estado.equals("cancelled");
    }
}
