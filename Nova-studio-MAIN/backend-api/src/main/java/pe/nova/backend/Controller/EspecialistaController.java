package pe.nova.backend.Controller;

import pe.nova.backend.service.EspecialistaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/empleados")
@CrossOrigin(origins = "http://localhost:4200")
public class EspecialistaController {

    @Autowired
    private EspecialistaService especialistaService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listarEspecialistas() {
        return ResponseEntity.ok(especialistaService.obtenerEspecialistas());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> registrarEspecialista(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Envía los datos del colaborador."));
        }
        return ejecutar(() -> especialistaService.crearEspecialista(
                texto(body, "nombre"), texto(body, "especialidad"), texto(body, "telefono")));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizarEspecialista(
            @PathVariable int id,
            @RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Envía los datos que deseas actualizar."));
        }
        return ejecutar(() -> especialistaService.actualizarEspecialista(
                id, texto(body, "nombre"), texto(body, "especialidad"), texto(body, "telefono")));
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<Map<String, Object>> cambiarEstado(
            @PathVariable int id,
            @RequestBody(required = false) Map<String, Object> body) {
        if (body == null || !body.containsKey("estado")) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Indica el nuevo estado del colaborador."));
        }
        Object valor = body.get("estado");
        boolean activo = valor instanceof Boolean ? (Boolean) valor : Boolean.parseBoolean(String.valueOf(valor));
        return ejecutar(() -> especialistaService.cambiarEstadoEspecialista(id, activo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminarEspecialista(@PathVariable int id) {
        return ejecutar(() -> especialistaService.eliminarEspecialista(id));
    }

    private String texto(Map<String, Object> body, String campo) {
        Object valor = body.get(campo);
        return valor == null ? "" : valor.toString();
    }

    private ResponseEntity<Map<String, Object>> ejecutar(Supplier<Map<String, Object>> operacion) {
        try {
            return ResponseEntity.ok(operacion.get());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
        }
    }
}
