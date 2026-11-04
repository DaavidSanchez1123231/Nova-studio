package pe.nova.backend.Controller;

import pe.nova.backend.model.ReservarCitaRequest;
import pe.nova.backend.service.ReservaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/citas/cliente")
public class CitaClienteController {

    @Autowired
    private ReservaService reservaService;

    // /api/citas/** ya exige token en SecurityConfig: no hay que tocarlo
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
}