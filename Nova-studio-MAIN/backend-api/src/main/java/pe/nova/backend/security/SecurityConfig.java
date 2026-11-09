package pe.nova.backend.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/api/publico/**").permitAll() 
                    .requestMatchers("/api/usuarios/login", "/api/usuarios/registro", "/api/usuarios/verificar").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/servicios").permitAll()
                    .requestMatchers("/api/dashboard/**", "/api/reportes/**").hasAnyAuthority("admin", "recepcion")
                    .requestMatchers("/api/clientes/**").hasAnyAuthority("admin", "recepcion")
                    .requestMatchers("/api/empleados", "/api/empleados/**").hasAuthority("admin")
                    .requestMatchers("/api/galeria/**").hasAnyAuthority("admin", "recepcion")
                    .requestMatchers("/api/configuracion/**").hasAuthority("admin")
                    .requestMatchers(HttpMethod.POST, "/api/servicios", "/api/servicios/**", 
                                   "/api/productos", "/api/productos/**", 
                                   "/api/promociones", "/api/promociones/**").hasAnyAuthority("admin", "recepcion")
                    .requestMatchers(HttpMethod.PUT, "/api/servicios", "/api/servicios/**", 
                                  "/api/productos", "/api/productos/**", 
                                  "/api/promociones", "/api/promociones/**").hasAnyAuthority("admin", "recepcion")
                    .requestMatchers(HttpMethod.DELETE, "/api/servicios", "/api/servicios/**", 
                                     "/api/productos", "/api/productos/**", 
                                     "/api/promociones", "/api/promociones/**").hasAuthority("admin")
                    
                    // El cliente reserva su propia cita: debe ir ANTES de la regla general de /api/citas/**
                    .requestMatchers("/api/citas/cliente/**").hasAnyAuthority("cliente", "admin", "recepcion")
                    .requestMatchers("/api/reservas/**").authenticated()
                    .requestMatchers("/api/ventas", "/api/ventas/**").hasAnyAuthority("admin", "recepcion")
                    .requestMatchers(HttpMethod.GET, "/api/productos", "/api/promociones").hasAnyAuthority("admin", "recepcion")
                    .requestMatchers("/api/citas", "/api/citas/**").hasAnyAuthority("admin", "recepcion")
                    .requestMatchers("/api/horarios", "/api/horarios/**").hasAnyAuthority("admin", "recepcion")
                    .anyRequest().authenticated()
                );
                
            http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsFilter corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:4200", 
            "http://localhost:5500",
            "http://localhost:5502"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "Origin"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsFilter(source);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:4200", 
            "http://localhost:5500",
            "http://localhost:5502"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "Origin"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}