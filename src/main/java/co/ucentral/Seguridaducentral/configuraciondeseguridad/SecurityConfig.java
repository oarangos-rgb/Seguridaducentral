package co.ucentral.Seguridaducentral.configuraciondeseguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /*
     * ==========================================================
     * POR QUE ESTA CONFIGURACION CAMBIO
     * ==========================================================
     *
     * Antes la cadena terminaba en:
     *
     *     .anyRequest().authenticated()
     *
     * con formLogin y httpBasic deshabilitados.
     *
     * Como esta app NO autentica contra Spring Security sino
     * contra la HttpSession, ningun request llegaba autenticado.
     * Resultado: cualquier URL fuera de la lista blanca devolvia
     * 403 Forbidden y caia en la Whitelabel Error Page.
     *
     * Eso es exactamente lo que pasaba con /seguimiento.
     *
     * El control de acceso real de este prototipo vive en los
     * controladores, leyendo el atributo "usuario" de la sesion.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());

        return http.build();
    }
}