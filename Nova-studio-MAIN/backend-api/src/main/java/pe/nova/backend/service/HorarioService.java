package pe.nova.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HorarioService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Jornada de atención: turnos de 1 hora. Cambia estas constantes si el salón abre distinto.
    private static final int HORA_APERTURA = 9;   // primer turno 09:00
    private static final int HORA_CIERRE = 18;    // último turno termina 18:00
    private static final int DIA_DESCANSO = 7;    // 7 = domingo (1 = lunes ... 7 = domingo)

    /**
     * Crea (solo si faltan) los turnos libres de un día para los especialistas ACTIVOS.
     * Así nadie tiene que cargar horarios por SQL: al consultar una fecha aparecen las horas libres.
     * No toca turnos existentes: respeta reservados, en proceso y los cancelados a mano.
     * @param idEspecialista null = todos los activos
     */
    @Transactional
    public void generarTurnosDelDia(String fecha, Integer idEspecialista) {
        LocalDate dia;
        try {
            dia = LocalDate.parse(fecha);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new IllegalArgumentException("La fecha no tiene un formato válido.");
        }

        ZoneId zonaPeru = ZoneId.of("America/Lima");
        LocalDate hoyPeru = LocalDate.now(zonaPeru);
        if (dia.isBefore(hoyPeru) || dia.getDayOfWeek().getValue() == DIA_DESCANSO) {
            return;
        }
        // Hoy solo se ofrecen horas que aún no pasaron
        LocalTime desde = dia.isEqual(hoyPeru) ? LocalTime.now(zonaPeru) : LocalTime.MIN;

        // Evita que dos consultas simultáneas generen turnos duplicados
        jdbcTemplate.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))", "turnos-" + fecha);

        String sql =
                "INSERT INTO public.horarios (id_especialista, fecha, hora_inicio, hora_fin, estado) " +
                "SELECT e.id_especialista, CAST(? AS DATE), t.hora, t.hora + INTERVAL '1 hour', 'DISPONIBLE' " +
                "FROM public.especialistas e " +
                "CROSS JOIN (SELECT make_time(g, 0, 0) AS hora FROM generate_series(" + HORA_APERTURA + ", " + (HORA_CIERRE - 1) + ") g) t " +
                "WHERE e.estado = TRUE " +
                "AND (CAST(? AS INTEGER) IS NULL OR e.id_especialista = CAST(? AS INTEGER)) " +
                "AND t.hora > CAST(? AS TIME) " +
                "AND NOT EXISTS (SELECT 1 FROM public.horarios x " +
                "    WHERE x.id_especialista = e.id_especialista AND x.fecha = CAST(? AS DATE) " +
                "    AND (x.hora_inicio = t.hora " +
                "         OR (UPPER(x.estado) <> 'CANCELADO' AND t.hora < x.hora_fin AND t.hora + INTERVAL '1 hour' > x.hora_inicio)))";
        jdbcTemplate.update(sql, fecha, idEspecialista, idEspecialista, desde.toString(), fecha);
    }

    @Transactional
    public List<Map<String, Object>> obtenerHorariosDisponibles(String fecha) {
        generarTurnosDelDia(fecha, null);
        String sql =
                "SELECT h.id_horario, CAST(h.fecha AS VARCHAR) AS fecha, " +
                "TO_CHAR(h.hora_inicio, 'HH24:MI') AS hora_inicio, " +
                "TO_CHAR(h.hora_fin, 'HH24:MI') AS hora_fin, " +
                "h.id_especialista, e.nombre AS especialista " +
                "FROM public.horarios h " +
                "INNER JOIN public.especialistas e ON e.id_especialista = h.id_especialista " +
                "WHERE h.fecha = CAST(? AS DATE) " +
                "AND h.fecha >= CURRENT_DATE " +
                "AND UPPER(h.estado) = 'DISPONIBLE' " +
                "AND e.estado = TRUE " +
                "ORDER BY h.hora_inicio, e.nombre";
        return jdbcTemplate.queryForList(sql, fecha);
    }

    public List<Map<String, Object>> obtenerHorariosPorEspecialista(int idEspecialista) {
        String sql =
                "SELECT id_horario, CAST(fecha AS VARCHAR) AS fecha, " +
                "TO_CHAR(hora_inicio, 'HH24:MI') AS hora_inicio, " +
                "TO_CHAR(hora_fin, 'HH24:MI') AS hora_fin, " +
                "UPPER(estado) AS estado " +
                "FROM public.horarios " +
                "WHERE id_especialista = ? " +
                "ORDER BY fecha, hora_inicio";
        return jdbcTemplate.queryForList(sql, idEspecialista);
    }

    @Transactional
    public Map<String, Object> crearHorario(int idEspecialista, String fecha,
                                             String horaInicio, String horaFin) {
        LocalDate dia;
        LocalTime inicio;
        LocalTime fin;
        try {
            dia = LocalDate.parse(fecha);
            inicio = LocalTime.parse(horaInicio);
            fin = LocalTime.parse(horaFin);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new IllegalArgumentException("La fecha o las horas no tienen un formato válido.");
        }

        ZoneId zonaPeru = ZoneId.of("America/Lima");
        LocalDate hoyPeru = LocalDate.now(zonaPeru);
        LocalTime horaPeru = LocalTime.now(zonaPeru);

        if (dia.isBefore(hoyPeru)) {
            throw new IllegalArgumentException("No puedes asignar un horario en una fecha pasada.");
        }
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException("La hora de fin debe ser posterior a la hora de inicio.");
        }
        if (dia.isEqual(hoyPeru) && !inicio.isAfter(horaPeru)) {
            throw new IllegalArgumentException("La hora de inicio debe ser posterior a la hora actual.");
        }

        Integer especialistaActivo = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.especialistas WHERE id_especialista = ? AND estado = TRUE",
                Integer.class, idEspecialista);
        if (especialistaActivo == null || especialistaActivo == 0) {
            throw new IllegalArgumentException("El colaborador no existe o está inactivo.");
        }

        String sqlCruce =
                "SELECT COUNT(*) FROM public.horarios " +
                "WHERE id_especialista = ? AND fecha = CAST(? AS DATE) " +
                "AND UPPER(estado) <> 'CANCELADO' " +
                "AND CAST(? AS TIME) < hora_fin " +
                "AND CAST(? AS TIME) > hora_inicio";
        Integer cruces = jdbcTemplate.queryForObject(
                sqlCruce, Integer.class, idEspecialista, fecha, horaInicio, horaFin);
        if (cruces != null && cruces > 0) {
            throw new IllegalArgumentException("El horario se cruza con otro turno de este colaborador.");
        }

        String sql = "INSERT INTO public.horarios " +
                "(id_especialista, fecha, hora_inicio, hora_fin, estado) " +
                "VALUES (?, CAST(? AS DATE), CAST(? AS TIME), CAST(? AS TIME), 'DISPONIBLE') " +
                "RETURNING id_horario";
        Integer id = jdbcTemplate.queryForObject(sql, Integer.class,
                idEspecialista, fecha, horaInicio, horaFin);

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("mensaje", "Horario creado correctamente.");
        respuesta.put("id_horario", id);
        return respuesta;
    }

    @Transactional
    public Map<String, Object> eliminarHorario(int idHorario) {
        Integer existe = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.horarios WHERE id_horario = ?",
                Integer.class, idHorario);
        if (existe == null || existe == 0) {
            throw new IllegalArgumentException("No se encontró el horario.");
        }

        Integer citasAsociadas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.citas WHERE id_horario = ?",
                Integer.class, idHorario);
        if (citasAsociadas != null && citasAsociadas > 0) {
            throw new IllegalArgumentException(
                    "No se puede cancelar este horario porque tiene citas asociadas. Gestiona primero esas citas.");
        }

        jdbcTemplate.update(
                "UPDATE public.horarios SET estado = 'CANCELADO' WHERE id_horario = ?",
                idHorario);
        return Map.of("mensaje", "Horario cancelado correctamente.");
    }
}
