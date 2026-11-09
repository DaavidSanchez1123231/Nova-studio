package pe.nova.backend.Controller;

import pe.nova.backend.model.ReservarCitaRequest;
import pe.nova.backend.model.ReprogramarCitaRequest;
import pe.nova.backend.service.CitaClienteService;
import pe.nova.backend.service.ReservaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/citas/cliente")
public class CitaClienteController {

    @Autowired
    private ReservaService reservaService;

    @Autowired
    private CitaClienteService citaClienteService;

    // Las rutas /api/citas/cliente/** se abren al rol cliente en SecurityConfig
    @PostMapping("/reservar")
    public ResponseEntity<Map<String, Object>> reservar(@RequestBody ReservarCitaRequest body,
                                                        Authentication auth) {
        if (body.idHorario() == null || body.idServicio() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("exito", false, "mensaje", "Faltan idHorario o idServicio."));
        }

        Map<String, Object> respuesta = reservaService.reservarParaCliente(
                auth.getName(), body.idHorario(), body.idServicio());

        // 200 si reservó; 409 (conflicto) si el horario ya no estaba libre
        boolean exito = Boolean.TRUE.equals(respuesta.get("exito"));
        return ResponseEntity.status(exito ? 200 : 409).body(respuesta);
    }

    /** Citas del cliente logueado (más recientes primero), con la marca puede_modificar. */
    @GetMapping("/mis-citas")
    public ResponseEntity<List<Map<String, Object>>> misCitas(Authentication auth) {
        return ResponseEntity.ok(citaClienteService.listarMisCitas(auth.getName()));
    }

    @PutMapping("/{idCita}/cancelar")
    public ResponseEntity<Map<String, Object>> cancelar(@PathVariable int idCita, Authentication auth) {
        try {
            return ResponseEntity.ok(citaClienteService.cancelar(auth.getName(), idCita));
        } catch (CitaClienteService.NoEncontradaException ex) {
            return ResponseEntity.status(404).body(Map.of("exito", false, "mensaje", ex.getMessage()));
        } catch (CitaClienteService.ReglaException ex) {
            return ResponseEntity.status(409).body(Map.of("exito", false, "mensaje", ex.getMessage()));
        }
    }

    @PutMapping("/{idCita}/reprogramar")
    public ResponseEntity<Map<String, Object>> reprogramar(@PathVariable int idCita,
                                                           @RequestBody ReprogramarCitaRequest body,
                                                           Authentication auth) {
        if (body == null || body.idHorario() == null) {
            return ResponseEntity.badRequest().body(Map.of("exito", false, "mensaje", "Falta idHorario."));
        }
        try {
            return ResponseEntity.ok(citaClienteService.reprogramar(auth.getName(), idCita, body.idHorario()));
        } catch (CitaClienteService.NoEncontradaException ex) {
            return ResponseEntity.status(404).body(Map.of("exito", false, "mensaje", ex.getMessage()));
        } catch (CitaClienteService.ReglaException ex) {
            return ResponseEntity.status(409).body(Map.of("exito", false, "mensaje", ex.getMessage()));
        }
    }
}
