package pe.nova.backend.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private final JwtUtil jwt = new JwtUtil();

    @Test
    void generaTokenValidoConEmailYRol() {
        String token = jwt.generarToken("ana@correo.com", "admin");

        assertTrue(jwt.isTokenValido(token));
        assertEquals("ana@correo.com", jwt.extraerClaims(token).getSubject());
        assertEquals("admin", jwt.extraerClaims(token).get("rol", String.class));
    }

    @Test
    void rechazaTokenManipulado() {
        assertFalse(jwt.isTokenValido("esto.no.es-un-token"));
        assertFalse(jwt.isTokenValido(jwt.generarToken("a@a.com", "admin") + "x"));
    }
}