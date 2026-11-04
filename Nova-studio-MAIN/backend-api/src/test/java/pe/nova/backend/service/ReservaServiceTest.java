package pe.nova.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock JdbcTemplate jdbcTemplate;
    @InjectMocks ReservaService service;

    @Test
    void consultaHorariosConElProcedimientoYParametros() {
        when(jdbcTemplate.queryForList("SELECT * FROM sp_horarios_disponibles(?, ?)", 5, "2026-10-20"))
                .thenReturn(List.of(Map.of("id_horario", 11)));

        assertEquals(1, service.obtenerHorariosDisponibles(5, "2026-10-20").size());
    }
}