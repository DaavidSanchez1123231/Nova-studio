package pe.nova.backend.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        System.out.println(">>> URL: " + request.getRequestURI());
System.out.println(">>> AUTH HEADER: " + authHeader); 

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);

            try {
                if (jwtUtil.isTokenValido(jwt) && SecurityContextHolder.getContext().getAuthentication() == null) {
                    
                    // 1. Extraemos toda la información del token
                    Claims claims = jwtUtil.extraerClaims(jwt);
                    String email = claims.getSubject();
                    String rol = claims.get("rol", String.class); 
                    System.out.println(">>> EMAIL: " + claims.getSubject());
                    System.out.println(">>> ROL EXTRAÍDO: [" + rol + "]");


                    // 2. Le asignamos la Autoridad (El Gafete)
                    List<SimpleGrantedAuthority> autoridades = new ArrayList<>();
                        if (rol != null) {
                            autoridades.add(new SimpleGrantedAuthority(rol.toLowerCase().trim()));
                        }
                        System.out.println(">>> AUTORIDADES: " + autoridades);

                    // 3. Registramos al usuario en la sesión actual con su rol
                    UsernamePasswordAuthenticationToken authToken = 
                            new UsernamePasswordAuthenticationToken(email, null, autoridades);
                    
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
                
            } catch (Exception e) {
    // Cambia esta línea — antes era silencioso
                        System.out.println(">>> JWT ERROR en " + request.getRequestURI() + ": " + e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }
    
}