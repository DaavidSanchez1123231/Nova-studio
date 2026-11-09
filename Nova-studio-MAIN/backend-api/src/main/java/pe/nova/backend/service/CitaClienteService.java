package pe.nova.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.nova.backend.repository.UsuarioRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Mis citas" del cliente: listar, cancelar y reprogramar.
 * Siempre sobre citas del usuario del token (nunca de otro).
 */
@Service
public class CitaClienteService {

    /** Regla: solo se puede cancelar o reprogramar hasta N horas antes de la cita. Cámbiala aquí. */
    public static final int HORAS_MINIMAS_ANTICIPACION = 2;

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    /** Error de regla de negocio (el controlador lo responde con 409). */
    public static class ReglaException extends RuntimeException {
        public ReglaException(String mensaje) { super(mensaje); }
    }

    /** La cita no existe o no es de este usuario (el controlador responde 404). */
    public static class NoEncontradaException extends RuntimeException {
        public NoEncontradaException(String mensaje) { super(mensaje); }
    }

    private int idUsuarioPorEmail(String email) {
        List<Map<String, Object>> filas = usuarioRepository.buscarPorEmail(email);
        if (filas.isEmpty()) {
            throw new IllegalStateException("Usuario del token no existe.");
        }
        return ((Number) filas.get(0).get("id_usuario")).intValue();
    }

    private boolean faltaSuficienteTiempo(LocalDate fecha, LocalTime horaInicio) {
        LocalDateTime inicio = LocalDateTime.of(fecha, horaInicio);
        return Duration.between(LocalDateTime.now(ZONA), inicio).toMinutes() < HORAS_MINIMAS_ANTICIPACION * 60L;
    }

    public List<Map<String, Object>> listarMisCitas(String email) {
        int idUsuario = idUsuarioPorEmail(email);
        List<Map<String, Object>> citas = jdbcTemplate.queryForList(
                "SELECT c.id_cita, c.estado, c.id_horario, c.id_servicio, c.id_especialista, " +
                "s.nombre AS servicio, s.precio, e.nombre AS especialista, " +
                "h.fecha AS fecha_date, h.hora_inicio AS inicio_time, " +
                "CAST(h.fecha AS VARCHAR) AS fecha, " +
                "TO_CHAR(h.hora_inicio, 'HH24:MI') AS hora_inicio, " +
                "TO_CHAR(h.hora_fin, 'HH24:MI') AS hora_fin " +
                "FROM public.citas c " +
                "JOIN public.horarios h ON h.id_horario = c.id_horario " +
                "LEFT JOIN public.servicios s ON s.id_servicio = c.id_servicio " +
                "LEFT JOIN public.especialistas e ON e.id_especialista = c.id_especialista " +
                "WHERE c.id_usuario = ? " +
                "ORDER BY h.fecha DESC, h.hora_inicio DESC", idUsuario);

        for (Map<String, Object> c : citas) {
            LocalDate fecha = ((java.sql.Date) c.remove("fecha_date")).toLocalDate();
            LocalTime inicio = ((java.sql.Time) c.remove("inicio_time")).toLocalTime();
            String estado = String.valueOf(c.get("estado"));
            boolean activa = estado.equals("confirmada") || estado.equals("pendiente_confirmacion");
            c.put("puede_modificar", activa && !faltaSuficienteTiempo(fecha, inicio));
        }
        return citas;
    }

    /** Bloquea la cita del usuario y valida estado y anticipación. Devuelve la fila. */
    private Map<String, Object> cargarCitaModificable(int idUsuario, int idCita, String accion) {
        List<Map<String, Object>> filas = jdbcTemplate.queryForList(
                "SELECT c.id_cita, c.estado, c.id_horario, c.id_especialista, h.fecha, h.hora_inicio " +
                "FROM public.citas c JOIN public.horarios h ON h.id_horario = c.id_horario " +
                "WHERE c.id_cita = ? AND c.id_usuario = ? FOR UPDATE OF c", idCita, idUsuario);
        if (filas.isEmpty()) {
            throw new NoEncontradaException("No se encontró la cita.");
        }
        Map<String, Object> cita = filas.get(0);
        String estado = String.valueOf(cita.get("estado"));
        if (!estado.equals("confirmada") && !estado.equals("pendiente_confirmacion")) {
            throw new ReglaException("Esta cita ya está " + estado + " y no se puede " + accion + ".");
        }
        LocalDate fecha = ((java.sql.Date) cita.get("fecha")).toLocalDate();
        LocalTime inicio = ((java.sql.Time) cita.get("hora_inicio")).toLocalTime();
        if (faltaSuficienteTiempo(fecha, inicio)) {
            throw new ReglaException("Solo puedes " + accion + " hasta " + HORAS_MINIMAS_ANTICIPACION +
                    " horas antes de la cita. Comunícate con el salón.");
        }
        return cita;
    }

    @Transactional
    public Map<String, Object> cancelar(String email, int idCita) {
        int idUsuario = idUsuarioPorEmail(email);
        Map<String, Object> cita = cargarCitaModificable(idUsuario, idCita, "cancelar");
        int idHorario = ((Number) cita.get("id_horario")).intValue();

        jdbcTemplate.update("UPDATE public.citas SET estado = 'cancelada' WHERE id_cita = ?", idCita);
        // El turno vuelve a quedar libre para otro cliente
        jdbcTemplate.update(
                "UPDATE public.horarios SET estado = 'DISPONIBLE', fecha_bloqueo = NULL " +
                "WHERE id_horario = ? AND UPPER(estado) IN ('RESERVADO', 'EN_PROCESO')", idHorario);
        return resultado("Tu cita fue cancelada. El horario quedó libre.");
    }

    @Transactional
    public Map<String, Object> reprogramar(String email, int idCita, int idHorarioNuevo) {
        int idUsuario = idUsuarioPorEmail(email);
        Map<String, Object> cita = cargarCitaModificable(idUsuario, idCita, "reprogramar");
        int idHorarioActual = ((Number) cita.get("id_horario")).intValue();
        int idEspecialista = ((Number) cita.get("id_especialista")).intValue();

        if (idHorarioNuevo == idHorarioActual) {
            throw new ReglaException("Elige un horario distinto al actual.");
        }

        // El nuevo turno debe ser del mismo especialista, estar libre y no haber pasado
        List<Map<String, Object>> nuevo = jdbcTemplate.queryForList(
                "SELECT fecha, hora_inicio FROM public.horarios " +
                "WHERE id_horario = ? AND id_especialista = ? AND UPPER(estado) = 'DISPONIBLE' " +
                "AND fecha >= CURRENT_DATE FOR UPDATE", idHorarioNuevo, idEspecialista);
        if (nuevo.isEmpty()) {
            throw new ReglaException("Ese horario ya no está disponible. Elige otro.");
        }
        LocalDate fechaNueva = ((java.sql.Date) nuevo.get(0).get("fecha")).toLocalDate();
        LocalTime inicioNuevo = ((java.sql.Time) nuevo.get(0).get("hora_inicio")).toLocalTime();
        if (faltaSuficienteTiempo(fechaNueva, inicioNuevo)) {
            throw new ReglaException("El nuevo horario debe ser con al menos " + HORAS_MINIMAS_ANTICIPACION +
                    " horas de anticipación.");
        }

        jdbcTemplate.update("UPDATE public.horarios SET estado = 'RESERVADO', fecha_bloqueo = NULL WHERE id_horario = ?", idHorarioNuevo);
        jdbcTemplate.update("UPDATE public.horarios SET estado = 'DISPONIBLE', fecha_bloqueo = NULL WHERE id_horario = ?", idHorarioActual);
        jdbcTemplate.update("UPDATE public.citas SET id_horario = ?, estado = 'confirmada' WHERE id_cita = ?", idHorarioNuevo, idCita);
        return resultado("Tu cita fue reprogramada correctamente.");
    }

    private Map<String, Object> resultado(String mensaje) {
        Map<String, Object> r = new HashMap<>();
        r.put("exito", true);
        r.put("mensaje", mensaje);
        return r;
    }
}
