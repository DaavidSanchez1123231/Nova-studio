package pe.nova.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {
    
    // 1. LLAVE FIJA: Esto evita que los tokens mueran al reiniciar el servidor
    private static final String SECRET_KEY_STRING = "K1mStudi0SuperSecretKeyForJwtAuth2026+";
    private static final Key KEY = Keys.hmacShaKeyFor(SECRET_KEY_STRING.getBytes());
    
    private final long EXPIRATION_TIME = 1000 * 60 * 60 * 10; // 10 horas

    public String generarToken(String email, String rol) {
        return Jwts.builder()
                .setSubject(email)
                .claim("rol", rol)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(KEY)
                .compact();
    }

    public Claims extraerClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isTokenValido(String token) {
        try {
            extraerClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}