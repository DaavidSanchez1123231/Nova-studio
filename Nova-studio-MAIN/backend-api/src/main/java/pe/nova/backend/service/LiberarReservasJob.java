package pe.nova.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LiberarReservasJob {

    @Autowired
    private ReservaService reservaService;

    @Scheduled(fixedDelay = 60_000)
    public void liberar() {
        try {
            reservaService.liberarReservasExpiradas();
        } catch (Exception e) {
            System.err.println("Error liberando reservas expiradas: " + e.getMessage());
        }
    }
}