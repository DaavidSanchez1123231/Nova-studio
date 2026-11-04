
package pe.nova.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ServicioService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Cacheable("serviciosCache")
    public List<Map<String, Object>> obtenerServiciosActivos() {

        String sql = """
            SELECT
                id_servicio,
                nombre,
                descripcion AS "desc",
                precio,
                duracion_minutos,
                imagen_url,
                categoria AS "categoriaId"
            FROM public.servicios
            WHERE estado = TRUE
            ORDER BY id_servicio DESC
            """;

        return jdbcTemplate.queryForList(sql);
    }

    @CacheEvict(value = "serviciosCache", allEntries = true)
    public Map<String, Object> crearServicio(
            String nombre,
            String descripcion,
            double precio,
            int duracionMinutos,
            String imagenUrl,
            String categoria) {

        validarServicio(nombre, precio, duracionMinutos, categoria);

        String sql = """
            INSERT INTO public.servicios
                (nombre, descripcion, precio,
                 duracion_minutos, imagen_url, categoria, estado)
            VALUES (?, ?, ?, ?, ?, ?, TRUE)
            """;

        jdbcTemplate.update(
            sql,
            nombre.trim(),
            descripcion,
            precio,
            duracionMinutos,
            imagenUrl,
            categoria
        );

        return Map.of(
            "mensaje", "Servicio creado correctamente."
        );
    }

    @CacheEvict(value = "serviciosCache", allEntries = true)
    public Map<String, Object> actualizarServicio(
            int id,
            String nombre,
            String descripcion,
            double precio,
            int duracionMinutos,
            String imagenUrl,
            String categoria) {

        validarServicio(nombre, precio, duracionMinutos, categoria);

        String sql = """
            UPDATE public.servicios
            SET
                nombre = ?,
                descripcion = ?,
                precio = ?,
                duracion_minutos = ?,
                imagen_url = ?,
                categoria = ?
            WHERE id_servicio = ?
              AND estado = TRUE
            """;

        int filas = jdbcTemplate.update(
            sql,
            nombre.trim(),
            descripcion,
            precio,
            duracionMinutos,
            imagenUrl,
            categoria,
            id
        );

        if (filas == 0) {
            throw new IllegalArgumentException(
                "El servicio no existe o está inactivo."
            );
        }

        return Map.of(
            "mensaje", "Servicio actualizado correctamente.",
            "id_servicio", id,
            "duracion_minutos", duracionMinutos
        );
    }

    @CacheEvict(value = "serviciosCache", allEntries = true)
    public Map<String, Object> eliminarServicio(int idServicio) {

        int filas = jdbcTemplate.update(
            """
            UPDATE public.servicios
            SET estado = FALSE
            WHERE id_servicio = ?
              AND estado = TRUE
            """,
            idServicio
        );

        if (filas == 0) {
            throw new IllegalArgumentException(
                "El servicio no existe o ya está inactivo."
            );
        }

        return Map.of(
            "mensaje", "Servicio inactivado correctamente."
        );
    }

    private void validarServicio(
            String nombre,
            double precio,
            int duracionMinutos,
            String categoria) {

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException(
                "El nombre del servicio es obligatorio."
            );
        }

        if (!Double.isFinite(precio) || precio <= 0) {
            throw new IllegalArgumentException(
                "El precio debe ser mayor que cero."
            );
        }

        if (duracionMinutos < 5 || duracionMinutos > 600
                || duracionMinutos % 5 != 0) {
            throw new IllegalArgumentException(
                "La duración debe estar entre 5 y 600 minutos, en intervalos de 5."
            );
        }

        if (categoria == null || !List.of(
                "cabello", "cejas", "uñas"
            ).contains(categoria)) {

            throw new IllegalArgumentException(
                "Selecciona una categoría válida."
            );
        }
    }
}
