package pe.nova.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginAttemptServiceTest {

    private long ahora;
    private LoginAttemptService servicio;
    private static final String CLAVE = "u:ana@correo.com";

    @BeforeEach
    void preparar() {
        ahora = 1_000_000_000L;
        servicio = new LoginAttemptService(() -> ahora);   // reloj falso: no hay que esperar de verdad
    }

    private void fallar(int veces) {
        for (int i = 0; i < veces; i++) servicio.registrarFallo(CLAVE, LoginAttemptService.GRATIS_POR_CUENTA);
    }

    @Test
    void losPrimerosTresFallosNoTienenEspera() {
        fallar(3);
        assertEquals(0, servicio.segundosRestantes(CLAVE));
    }

    @Test
    void laEsperaSeDuplicaConCadaFallo() {
        fallar(4); assertEquals(2, servicio.segundosRestantes(CLAVE));
        ahora += 2_000; fallar(1); assertEquals(4, servicio.segundosRestantes(CLAVE));
        ahora += 4_000; fallar(1); assertEquals(8, servicio.segundosRestantes(CLAVE));
    }

    @Test
    void laEsperaNuncaPasaDeQuinceMinutos() {
        for (int i = 0; i < 30; i++) { fallar(1); ahora += servicio.segundosRestantes(CLAVE) * 1000; }
        fallar(1);
        assertEquals(900, servicio.segundosRestantes(CLAVE));
    }

    @Test
    void unInicioCorrectoReiniciaElContador() {
        fallar(6);
        servicio.registrarExito(CLAVE);
        assertEquals(0, servicio.segundosRestantes(CLAVE));
        fallar(3);
        assertEquals(0, servicio.segundosRestantes(CLAVE));
    }

    @Test
    void trasMediaHoraSinFallosSeOlvida() {
        fallar(8);
        ahora += 31 * 60 * 1000L;
        assertEquals(0, servicio.segundosRestantes(CLAVE));
    }

    @Test
    void cadaCuentaTieneSuPropioContador() {
        fallar(8);
        assertEquals(0, servicio.segundosRestantes("u:otra@correo.com"));
    }
}
