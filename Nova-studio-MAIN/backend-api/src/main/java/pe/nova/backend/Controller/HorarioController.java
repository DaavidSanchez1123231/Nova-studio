package pe.nova.backend.Controller;

import pe.nova.backend.service.HorarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/horarios")
@CrossOrigin(origins = "http://localhost:4200")
public class HorarioController {

    @Autowired
    private HorarioService horarioService;

    @GetMapping("/disponibles")
    public ResponseEntity<?> listarHorariosDisponibles(@RequestParam String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Selecciona una fecha."));
        }
        try {
            return ResponseEntity.ok(horarioService.obtenerHorariosDisponibles(fecha));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
        }
    }

    @GetMapping("/especialista/{id}")
    public ResponseEntity<List<Map<String, Object>>> listarHorariosDelEspecialista(@PathVariable int id) {
        return ResponseEntity.ok(horarioService.obtenerHorariosPorEspecialista(id));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> asignarHorario(
            @RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Envía los datos del horario."));
        }

        try {
            int id = Integer.parseInt(String.valueOf(body.get("id_especialista")));
            String fecha = requerido(body, "fecha");
            String inicio = requerido(body, "hora_inicio");
            String fin = requerido(body, "hora_fin");
            return ejecutar(() -> horarioService.crearHorario(id, fecha, inicio, fin));
        } catch (NumberFormatException ex) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Selecciona un colaborador válido."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminarHorario(@PathVariable int id) {
        return ejecutar(() -> horarioService.eliminarHorario(id));
    }

    private String requerido(Map<String, Object> body, String campo) {
        Object valor = body.get(campo);
        if (valor == null || valor.toString().isBlank()) {
            throw new IllegalArgumentException("Completa el campo: " + campo + ".");
        }
        return valor.toString().trim();
    }

    private ResponseEntity<Map<String, Object>> ejecutar(Supplier<Map<String, Object>> operacion) {
        try {
            return ResponseEntity.ok(operacion.get());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
        }
    }
}
