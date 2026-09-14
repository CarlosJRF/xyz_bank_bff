package config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Deshabilitado para APIs REST
            .authorizeHttpRequests(auth -> auth
                // Autorización granular por prefijo de canal
                .requestMatchers("/api/web/**").hasRole("WEB")
                .requestMatchers("/api/movil/**").hasRole("MOVIL")
                .requestMatchers("/api/cajero/**").hasRole("CAJERO")
                .anyRequest().authenticated()
            )
            .httpBasic(withDefaults()); // Habilita envío de tokens Basic Auth
        
        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        UserDetails userWeb = User.withUsername("clienteWeb")
            .password("{noop}web123").roles("WEB").build();
            
        UserDetails userMovil = User.withUsername("clienteMovil")
            .password("{noop}movil123").roles("MOVIL").build();
            
        UserDetails userCajero = User.withUsername("atmXyz")
            .password("{noop}atm123").roles("CAJERO").build();

        return new InMemoryUserDetailsManager(userWeb, userMovil, userCajero);
    }
}
