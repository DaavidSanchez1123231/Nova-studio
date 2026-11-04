package pe.nova.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class EspecialistaService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Devuelve activos e inactivos para que la tabla refleje el estado real. */
    public List<Map<String, Object>> obtenerEspecialistas() {
        String sql = "SELECT id_especialista, nombre, especialidad, telefono, estado " +
                     "FROM public.especialistas " +
                     "ORDER BY estado DESC, nombre ASC";
        return jdbcTemplate.queryForList(sql);
    }

    @Transactional
    public Map<String, Object> crearEspecialista(String nombre, String especialidad, String telefono) {
        validarDatos(nombre, especialidad, telefono);

        String sql = "INSERT INTO public.especialistas (nombre, especialidad, telefono, estado) " +
                     "VALUES (?, ?, ?, TRUE) RETURNING id_especialista";
        Integer id = jdbcTemplate.queryForObject(sql, Integer.class,
                nombre.trim(), especialidad.trim(), telefono.trim());

        return Map.of(
                "mensaje", "Colaborador registrado correctamente.",
                "id_especialista", id
        );
    }

    @Transactional
    public Map<String, Object> actualizarEspecialista(int id, String nombre,
                                                       String especialidad, String telefono) {
        validarDatos(nombre, especialidad, telefono);
        verificarExiste(id);

        String sql = "UPDATE public.especialistas " +
                     "SET nombre = ?, especialidad = ?, telefono = ? " +
                     "WHERE id_especialista = ?";
        jdbcTemplate.update(sql, nombre.trim(), especialidad.trim(), telefono.trim(), id);

        return Map.of("mensaje", "Datos del colaborador actualizados correctamente.");
    }

    @Transactional
    public Map<String, Object> cambiarEstadoEspecialista(int id, boolean activo) {
        verificarExiste(id);
        String sql = "UPDATE public.especialistas SET estado = ? WHERE id_especialista = ?";
        jdbcTemplate.update(sql, activo, id);

        return Map.of("mensaje", activo
                ? "Colaborador reactivado correctamente."
                : "Colaborador dado de baja correctamente.");
    }

    public Map<String, Object> eliminarEspecialista(int id) {
        // Baja lógica: no borra al colaborador ni rompe las referencias históricas.
        return cambiarEstadoEspecialista(id, false);
    }

    private void verificarExiste(int id) {
        Integer cantidad = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.especialistas WHERE id_especialista = ?",
                Integer.class, id);
        if (cantidad == null || cantidad == 0) {
            throw new IllegalArgumentException("No existe un colaborador con ese identificador.");
        }
    }

    private void validarDatos(String nombre, String especialidad, String telefono) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("Ingresa el nombre completo del colaborador.");
        }
        if (nombre.trim().length() > 50) {
            throw new IllegalArgumentException("El nombre no puede superar los 50 caracteres.");
        }
        if (especialidad == null || especialidad.trim().isEmpty()) {
            throw new IllegalArgumentException("Selecciona una especialidad.");
        }
        if (especialidad.trim().length() > 50) {
            throw new IllegalArgumentException("La especialidad no puede superar los 50 caracteres.");
        }
        if (telefono == null || !telefono.trim().matches("[0-9]{9}")) {
            throw new IllegalArgumentException("El teléfono debe contener exactamente 9 dígitos.");
        }
    }
}
