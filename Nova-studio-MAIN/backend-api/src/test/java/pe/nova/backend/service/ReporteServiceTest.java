package pe.nova.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    @Mock JdbcTemplate jdbc;

    @SuppressWarnings("unchecked")
    @Test
    void calculaLaUtilidadRestandoElPorcentajeDeCostos() {
        ReporteService servicio = new ReporteService(jdbc, new BigDecimal("0.30"), "NOVA Beauty Salon");
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), any(Timestamp.class), any(Timestamp.class)))
                .thenReturn(new BigDecimal("1000.00"));
        when(jdbc.queryForList(anyString(), any(Date.class), any(Date.class))).thenReturn(List.of());

        Map<String, Object> balance = (Map<String, Object>)
                servicio.dashboard(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20)).get("balance");

        assertEquals(0, new BigDecimal("1000.00").compareTo((BigDecimal) balance.get("ingresosBrutos")));
        assertEquals(0, new BigDecimal("300.00").compareTo((BigDecimal) balance.get("insumosComisiones")));
        assertEquals(0, new BigDecimal("700.00").compareTo((BigDecimal) balance.get("utilidadNeta")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void sinVentasTodoQuedaEnCero() {
        ReporteService servicio = new ReporteService(jdbc, new BigDecimal("0.30"), "NOVA Beauty Salon");
        when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), any(Timestamp.class), any(Timestamp.class)))
                .thenReturn(null);
        when(jdbc.queryForList(anyString(), any(Date.class), any(Date.class))).thenReturn(List.of());

        Map<String, Object> balance = (Map<String, Object>)
                servicio.dashboard(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20)).get("balance");

        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) balance.get("utilidadNeta")));
    }
}
