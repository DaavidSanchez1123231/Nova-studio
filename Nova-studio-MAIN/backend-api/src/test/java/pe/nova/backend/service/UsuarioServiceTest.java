package pe.nova.backend.service;

import pe.nova.backend.repository.UsuarioRepository;
import pe.nova.backend.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock JdbcTemplate jdbcTemplate;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock UsuarioRepository usuarioRepository;
    @Mock EmailService emailService;
    @InjectMocks UsuarioService service;

    private Map<String, Object> usuario(boolean verificada) {
        return Map.of("id_usuario", 7, "nombre", "Ana", "rol", " ADMIN ",
                "contraseña", "hash", "cuenta_verificada", verificada);
    }

    @Test
    void loginCorrectoDevuelveTokenYRolNormalizado() {
        when(jdbcTemplate.queryForList(anyString(), eq("ana@correo.com"))).thenReturn(List.of(usuario(true)));
        when(passwordEncoder.matches("1234", "hash")).thenReturn(true);
        when(jwtUtil.generarToken("ana@correo.com", "admin")).thenReturn("tok");

        Map<String, Object> r = service.login("ana@correo.com", "1234");

        assertEquals("Login exitoso", r.get("mensaje"));
        assertEquals("tok", r.get("token"));
        assertEquals(7, r.get("id_usuario"));
    }

    @Test
    void loginConUsuarioInexistente() {
        when(jdbcTemplate.queryForList(anyString(), eq("x@x.com"))).thenReturn(List.of());

        Map<String, Object> r = service.login("x@x.com", "1234");

        assertEquals("El usuario no existe.", r.get("mensaje"));
        assertFalse(r.containsKey("token"));
    }

    @Test
    void loginConCuentaSinVerificarNoGeneraToken() {
        when(jdbcTemplate.queryForList(anyString(), eq("ana@correo.com"))).thenReturn(List.of(usuario(false)));

        Map<String, Object> r = service.login("ana@correo.com", "1234");

        assertEquals("Debe verificar su correo antes de iniciar sesión.", r.get("mensaje"));
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void loginConPasswordIncorrecto() {
        when(jdbcTemplate.queryForList(anyString(), eq("ana@correo.com"))).thenReturn(List.of(usuario(true)));
        when(passwordEncoder.matches("mala", "hash")).thenReturn(false);

        Map<String, Object> r = service.login("ana@correo.com", "mala");

        assertEquals("Credenciales incorrectas.", r.get("mensaje"));
        verify(jwtUtil, never()).generarToken(any(), any());
    }

    @Test
    void registroGuardaPasswordCifradaYSigueSiFallaElCorreo() {
        when(passwordEncoder.encode("1234")).thenReturn("hash");
        when(jdbcTemplate.queryForMap(anyString(), eq("Ana"), eq("ana@correo.com"), eq("999"), eq("hash")))
                .thenReturn(Map.of("mensaje", "ok"));
        doThrow(new RuntimeException("SMTP caído")).when(emailService)
                .enviarCorreoVerificacion("ana@correo.com", "Ana");

        Map<String, Object> r = service.registrarUsuario("Ana", "ana@correo.com", "999", "1234");

        assertEquals("ok", r.get("mensaje"));   // el fallo del correo no rompe el registro
    }
}