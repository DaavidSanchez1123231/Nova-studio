package pe.nova.backend.repository;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UsuarioRepository {
    @Autowired private JdbcTemplate jdbcTemplate;

    public Map<String, Object> registrarUsuario(String nombre, String email, String pass) {
        return jdbcTemplate.queryForMap("SELECT * FROM sp_registrar_usuario(?, ?, ?)", nombre, email, pass);
    }
    public List<Map<String, Object>> buscarPorEmail(String email) {
        return jdbcTemplate.queryForList("SELECT * FROM Usuarios WHERE LOWER(email) = LOWER(?)", email);
    }
   
    public int actualizarRol(int idUsuario, String nuevoRol) {
        String sql = "UPDATE Usuarios SET rol = ? WHERE id_usuario = ?";
        return jdbcTemplate.update(sql, nuevoRol, idUsuario);
    }
    public int verificarCuenta(String email) {
        String sql = "UPDATE Usuarios SET cuenta_verificada = TRUE WHERE LOWER(email) = LOWER(?)";
        return jdbcTemplate.update(sql, email);
    }
}
