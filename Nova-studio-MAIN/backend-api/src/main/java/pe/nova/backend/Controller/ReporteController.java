package pe.nova.backend.Controller;

import pe.nova.backend.service.ReporteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    @Autowired private ReporteService reporteService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(@RequestParam(required = false) String desde,
                                       @RequestParam(required = false) String hasta) {
        try {
            LocalDate[] rango = rango(desde, hasta);
            return ResponseEntity.ok(reporteService.dashboard(rango[0], rango[1]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return error("Rango de fechas inválido. Usa el formato AAAA-MM-DD y que «desde» no sea posterior a «hasta».");
        }
    }

    @GetMapping("/exportar/pdf")
    public ResponseEntity<?> pdf(@RequestParam(required = false) String desde,
                                 @RequestParam(required = false) String hasta) {
        try {
            LocalDate[] rango = rango(desde, hasta);
            byte[] contenido = reporteService.generarPdf(rango[0], rango[1]);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Reporte_NOVA_" + rango[0] + "_" + rango[1] + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(contenido);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return error("Rango de fechas inválido. Usa el formato AAAA-MM-DD y que «desde» no sea posterior a «hasta».");
        }
    }

    /** Sin parámetros: del día 1 del mes actual a hoy. Máximo 366 días. */
    private static LocalDate[] rango(String desde, String hasta) {
       LocalDate fin = (hasta == null || hasta.isBlank())
        ? LocalDate.now(ZoneId.of("America/Lima"))
        : LocalDate.parse(hasta.trim());
        LocalDate ini = (desde == null || desde.isBlank()) ? fin.withDayOfMonth(1) : LocalDate.parse(desde.trim());
        if (ini.isAfter(fin) || ini.plusDays(365).isBefore(fin)) {
    throw new IllegalArgumentException("rango");
}
        return new LocalDate[]{ini, fin};
    }

    private static ResponseEntity<Map<String, Object>> error(String mensaje) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("mensaje", mensaje);
        return ResponseEntity.badRequest().body(cuerpo);
    }
}
