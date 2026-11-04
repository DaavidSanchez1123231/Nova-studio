package pe.nova.backend.service;
import pe.nova.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReservaService {

    // Textos que devuelven tus sp_* cuando todo sale bien. Si cambias el mensaje en SQL, cámbialo aquí.
    private static final String OK_BLOQUEO = "Horario bloqueado";
    private static final String OK_CONFIRMACION = "Cita confirmada";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    // ---- tus 3 métodos originales (sin cambios) ----
    public List<Map<String, Object>> obtenerHorariosDisponibles(int idEspecialista, String fecha) {
        String sql = "SELECT * FROM sp_horarios_disponibles(?, ?)";
        return jdbcTemplate.queryForList(sql, idEspecialista, fecha);
    }

    public Map<String, Object> reservarCitaTemporal(int idUsuario, int idHorario, int idServicio) {
        String sql = "SELECT * FROM sp_reservar_cita_temporal(?, ?, ?)";
        return jdbcTemplate.queryForMap(sql, idUsuario, idHorario, idServicio);
    }

    public Map<String, Object> confirmarCita(int idHorario, int idUsuario) {
        String sql = "SELECT * FROM sp_confirmar_cita(?, ?)";
        return jdbcTemplate.queryForMap(sql, idHorario, idUsuario);
    }

    // ---- NUEVO ----

    /** El JWT guarda el email; de ahí sale el id_usuario real (nunca del body). */
    public int idUsuarioPorEmail(String email) {
        List<Map<String, Object>> filas = usuarioRepository.buscarPorEmail(email);
        if (filas.isEmpty()) {
            throw new IllegalStateException("Usuario del token no existe.");
        }
        return ((Number) filas.get(0).get("id_usuario")).intValue();
    }

    /**
     * Reserva en un paso: bloquear + confirmar en UNA transacción.
     * Devuelve {exito, mensaje}. Si algo falla, no queda nada a medias (rollback).
     */
    @Transactional
    public Map<String, Object> reservarParaCliente(String email, int idHorario, int idServicio) {
        int idUsuario = idUsuarioPorEmail(email);

        Map<String, Object> bloqueo = reservarCitaTemporal(idUsuario, idHorario, idServicio);
        if (!mensajeEmpiezaCon(bloqueo, OK_BLOQUEO)) {
            return resultado(false, bloqueo.get("mensaje"));   // p. ej. "El horario ya fue tomado o no existe."
        }

        Map<String, Object> confirmacion = confirmarCita(idHorario, idUsuario);
        if (!mensajeEmpiezaCon(confirmacion, OK_CONFIRMACION)) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();   // deshace el bloqueo
            return resultado(false, confirmacion.get("mensaje"));
        }
        return resultado(true, confirmacion.get("mensaje"));
    }

    /** Libera los horarios EN_PROCESO con más de 10 min sin confirmar. */
    public void liberarReservasExpiradas() {
        jdbcTemplate.queryForList("SELECT * FROM sp_liberar_reservas_expiradas()");
    }

    private boolean mensajeEmpiezaCon(Map<String, Object> fila, String prefijo) {
        Object m = fila.get("mensaje");
        return m != null && m.toString().startsWith(prefijo);
    }

    private Map<String, Object> resultado(boolean exito, Object mensaje) {
        Map<String, Object> r = new HashMap<>();
        r.put("exito", exito);
        r.put("mensaje", mensaje);
        return r;
    }
}