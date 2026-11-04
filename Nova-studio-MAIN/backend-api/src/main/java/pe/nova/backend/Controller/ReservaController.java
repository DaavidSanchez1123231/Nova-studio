package pe.nova.backend.Controller;

import pe.nova.backend.model.ConfirmacionDTO;
import pe.nova.backend.model.ReservaTemporalDTO;
import pe.nova.backend.service.ReservaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reservas")
@CrossOrigin(origins = "*") 
public class ReservaController {

    @Autowired
    private ReservaService reservaService;


    @GetMapping("/disponibles")
    public ResponseEntity<List<Map<String, Object>>> getHorariosDisponibles(
            @RequestParam int idEspecialista, 
            @RequestParam String fecha) {
        
        List<Map<String, Object>> horarios = reservaService.obtenerHorariosDisponibles(idEspecialista, fecha);
        return ResponseEntity.ok(horarios);
    }


    @PostMapping("/bloquear")
    public ResponseEntity<Map<String, Object>> bloquearHorario(@RequestBody ReservaTemporalDTO peticion) {
        Map<String, Object> respuesta = reservaService.reservarCitaTemporal(
                peticion.getIdUsuario(),
                peticion.getIdHorario(),
                peticion.getIdServicio()
        );
        return ResponseEntity.ok(respuesta);
    }

    @PostMapping("/confirmar")
    public ResponseEntity<Map<String, Object>> confirmarCita(@RequestBody ConfirmacionDTO peticion) {
        Map<String, Object> respuesta = reservaService.confirmarCita(
                peticion.getIdHorario(),
                peticion.getIdUsuario()
        );
        return ResponseEntity.ok(respuesta);
    }
}