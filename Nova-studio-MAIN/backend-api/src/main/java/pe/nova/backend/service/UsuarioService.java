package pe.nova.backend.service;

import pe.nova.backend.repository.UsuarioRepository;
import pe.nova.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UsuarioService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder; 

    @Autowired
    private JwtUtil jwtUtil; 

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private EmailService emailService;

    public Map<String, Object> registrarUsuario(String nombre, String email, String telefono, String password) {
        String passwordEncriptada = passwordEncoder.encode(password);
        String sql = "SELECT * FROM sp_registrar_usuario(?, ?, ?, ?)";
        Map<String, Object> respuesta = jdbcTemplate.queryForMap(sql, nombre, email, telefono, passwordEncriptada);
        
        
        try {
            emailService.enviarCorreoVerificacion(email, nombre);
        } catch (Exception e) {
            System.err.println("Error al enviar correo: " + e.getMessage());
        }
        
        return respuesta;
    }

    public Map<String, Object> login(String email, String passwordTextoPlano) {
        Map<String, Object> respuesta = new HashMap<>();

        String sql = "SELECT * FROM Usuarios WHERE LOWER(email) = LOWER(?)";
        List<Map<String, Object>> usuarios = jdbcTemplate.queryForList(sql, email);

        if (usuarios.isEmpty()) {
            respuesta.put("mensaje", "El usuario no existe.");
            return respuesta;
        }

        Map<String, Object> usuario = usuarios.get(0);
        
        Boolean cuentaVerificada = (Boolean) usuario.get("cuenta_verificada");
        if (cuentaVerificada != null && !cuentaVerificada) {
            respuesta.put("mensaje", "Debe verificar su correo antes de iniciar sesión.");
            return respuesta;
        }

        String passwordEncriptadaBD = (String) usuario.get("contraseña");
        
        if (passwordEncoder.matches(passwordTextoPlano, passwordEncriptadaBD)) {
            String rol = (String) usuario.get("rol");
            String token = jwtUtil.generarToken(email, rol.toLowerCase().trim());
            respuesta.put("mensaje", "Login exitoso");
            respuesta.put("token", token); 
            respuesta.put("id_usuario", usuario.get("id_usuario"));
            respuesta.put("nombre", usuario.get("nombre"));
            respuesta.put("rol", rol);
        } else {
            respuesta.put("mensaje", "Credenciales incorrectas.");
        }

        return respuesta;
    }

    public int actualizarRolPrueba(int idUsuario, String nuevoRol) {
        // Ahora usamos la 'u' minúscula porque ya tenemos la variable inyectada arriba
        return usuarioRepository.actualizarRol(idUsuario, nuevoRol);
    }
    public boolean procesarVerificacion(String email) {
        int filasAfectadas = usuarioRepository.verificarCuenta(email);
        return filasAfectadas > 0;
    }
    
}