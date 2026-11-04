
package pe.nova.backend.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/galeria")
@CrossOrigin(origins = "http://localhost:4200")
public class GaleriaController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // LISTAR TRABAJOS Y SUS DOS IMÁGENES
    

@GetMapping
public ResponseEntity<List<Map<String, Object>>> listarImagenes() {

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

    return ResponseEntity.ok(
        jdbcTemplate.queryForList(sql)
    );
}



    // GUARDAR TRABAJO CON FOTO ANTES Y DESPUÉS
    @PostMapping
    public ResponseEntity<Map<String, String>> agregarImagen(
            @RequestBody Map<String, Object> body) {

        String titulo = obtenerTexto(body, "titulo");
        String categoria = obtenerTexto(body, "categoria");
        String imagenAntes = obtenerTexto(body, "url_imagen");
        String imagenDespues =
            obtenerTexto(body, "url_imagen_despues");

        if (titulo.isEmpty()
                || categoria.isEmpty()
                || imagenAntes.isEmpty()
                || imagenDespues.isEmpty()) {

            return ResponseEntity.badRequest().body(
                Map.of(
                    "mensaje",
                    "Debes completar el título y las dos imágenes."
                )
            );
        }

        String sql = """
            INSERT INTO public.galeria
                (titulo, categoria, url_imagen,
                 url_imagen_despues, estado)
            VALUES (?, ?, ?, ?, '1')
            """;

        jdbcTemplate.update(
            sql,
            titulo,
            categoria,
            imagenAntes,
            imagenDespues
        );

        return ResponseEntity.ok(
            Map.of("mensaje", "Trabajo publicado correctamente.")
        );
    }

    // ELIMINAR TRABAJO
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> eliminarImagen(
            @PathVariable int id) {

        int filas = jdbcTemplate.update(
            """
            UPDATE public.galeria
            SET estado = '0'
            WHERE id_imagen = ?
            """,
            id
        );

        if (filas == 0) {
            return ResponseEntity.status(404).body(
                Map.of("mensaje", "Trabajo no encontrado.")
            );
        }

        return ResponseEntity.ok(
            Map.of("mensaje", "Trabajo eliminado correctamente.")
        );
    }

    // LEER CAMPOS DEL JSON
    private String obtenerTexto(
            Map<String, Object> body, String campo) {

        Object valor = body.get(campo);

        return valor == null
            ? ""
            : valor.toString().trim();
    }
}
