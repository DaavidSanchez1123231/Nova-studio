package pe.nova.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CuentaServiceTest {

    @Mock JdbcTemplate jdbc;
    @Mock PasswordEncoder encoder;
    @Mock EmailService email;
    CuentaService servicio;

    @BeforeEach
    void preparar() {
        servicio = new CuentaService(jdbc, encoder, email, "http://localhost:4200");
    }

    private Map<String, Object> fila(Object... paresClaveValor) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < paresClaveValor.length; i += 2) m.put((String) paresClaveValor[i], paresClaveValor[i + 1]);
        return m;
    }

    @Test
    void rechazaContraseniasCortasYMuyLargas() {
        assertNotNull(servicio.validarPassword("corta"));
        assertNotNull(servicio.validarPassword("x".repeat(73)));
        assertNull(servicio.validarPassword("ClaveSegura#1"));
    }

    @Test
    void restablecerConClaveCortaNoTocaLaBase() {
        assertNotNull(servicio.restablecer("token", "corta"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void restablecerConTokenValidoCambiaLaClave() {
        when(encoder.encode("ClaveNueva#1")).thenReturn("hash");
        when(jdbc.update(anyString(), eq("hash"), anyString())).thenReturn(1);
        assertNull(servicio.restablecer("token-abc", "ClaveNueva#1"));
    }

    @Test
    void restablecerConTokenVencidoOInexistenteDevuelveMensaje() {
        when(encoder.encode("ClaveNueva#1")).thenReturn("hash");
        when(jdbc.update(anyString(), eq("hash"), anyString())).thenReturn(0);
        assertNotNull(servicio.restablecer("token-vencido", "ClaveNueva#1"));
    }

    @Test
    void cambiarPasswordConActualIncorrectaNoActualiza() {
        when(jdbc.queryForList(anyString(), eq("ana@correo.com"))).thenReturn(List.of(fila("contraseña", "hashViejo")));
        when(encoder.matches("mala", "hashViejo")).thenReturn(false);

        assertEquals("La contraseña actual es incorrecta.", servicio.cambiarPassword("ana@correo.com", "mala", "ClaveNueva#1"));

        verify(jdbc).queryForList(anyString(), eq("ana@correo.com"));
        verifyNoMoreInteractions(jdbc);   // no hubo ningún UPDATE
    }

    @Test
    void cambiarPasswordExigeUnaClaveDistinta() {
        when(jdbc.queryForList(anyString(), eq("ana@correo.com"))).thenReturn(List.of(fila("contraseña", "hashViejo")));
        when(encoder.matches("ClaveVieja#1", "hashViejo")).thenReturn(true);

        assertNotNull(servicio.cambiarPassword("ana@correo.com", "ClaveVieja#1", "ClaveVieja#1"));
        verify(encoder, never()).encode(anyString());
    }

    @Test
    void recuperacionConCorreoInexistenteNoEnviaNada() {
        when(jdbc.queryForList(anyString(), eq("nadie@correo.com"))).thenReturn(List.of());
        servicio.solicitarRecuperacion("nadie@correo.com");
        verifyNoInteractions(email);
    }

    @Test
    void recuperacionEnviaEnlaceConTokenAlFrontend() {
        when(jdbc.queryForList(anyString(), eq("ana@correo.com"))).thenReturn(List.of(fila("id_usuario", 7, "nombre", "Ana")));

        servicio.solicitarRecuperacion("ana@correo.com");

        ArgumentCaptor<String> enlace = ArgumentCaptor.forClass(String.class);
        verify(email).enviarCorreoRecuperacion(eq("ana@correo.com"), eq("Ana"), enlace.capture());
        assertTrue(enlace.getValue().startsWith("http://localhost:4200/restablecer?token="));
        assertTrue(enlace.getValue().length() > 60);   // token de 32 bytes en Base64
    }

    @Test
    void verificarConTokenMalFormadoNoConsultaLaBase() {
        Map<String, Object> r = servicio.verificarPorToken("no-es-un-uuid");
        assertEquals(false, r.get("ok"));
        verifyNoInteractions(jdbc);
    }
}
