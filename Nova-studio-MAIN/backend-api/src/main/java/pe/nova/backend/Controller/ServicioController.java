
package pe.nova.backend.Controller;

import pe.nova.backend.service.ServicioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/servicios")
@CrossOrigin(origins = "http://localhost:4200")
public class ServicioController {

    @Autowired
    private ServicioService servicioService;

    @GetMapping
    public ResponseEntity<?> listarServicios() {
        return ResponseEntity.ok(servicioService.obtenerServiciosActivos());
    }

    @PostMapping
    public ResponseEntity<?> registrarServicio(
            @RequestBody Map<String, Object> body) {

        try {
            return ResponseEntity.ok(
                servicioService.crearServicio(
                    texto(body, "nombre"),
                    texto(body, "desc"),
                    numeroDecimal(body, "precio"),
                    duracion(body),
                    texto(body, "imagen_url"),
                    texto(body, "categoriaId")
                )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("mensaje", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarServicio(
            @PathVariable int id,
            @RequestBody Map<String, Object> body) {

        try {
            return ResponseEntity.ok(
                servicioService.actualizarServicio(
                    id,
                    texto(body, "nombre"),
                    texto(body, "desc"),
                    numeroDecimal(body, "precio"),
                    duracion(body),
                    texto(body, "imagen_url"),
                    texto(body, "categoriaId")
                )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("mensaje", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarServicio(@PathVariable int id) {
        return ResponseEntity.ok(servicioService.eliminarServicio(id));
    }

    private String texto(Map<String, Object> body, String campo) {
        Object valor = body.get(campo);
        return valor == null ? "" : valor.toString().trim();
    }

    private double numeroDecimal(
            Map<String, Object> body, String campo) {

        try {
            return Double.parseDouble(texto(body, campo));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "El precio debe ser un número válido."
            );
        }
    }

    private int duracion(Map<String, Object> body) {
        try {
            return Integer.parseInt(
                texto(body, "duracion_minutos")
            );
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "La duración debe ser un número entero."
            );
        }
    }
}
