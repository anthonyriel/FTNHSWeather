package edu.ftnhs.weather_manager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Critical for @PreAuthorize to work
public class SecurityConfig {

    private final CookieAuthenticationFilter cookieAuthenticationFilter;

    public SecurityConfig(CookieAuthenticationFilter cookieAuthenticationFilter) {
        this.cookieAuthenticationFilter = cookieAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                // REST endpoints are called via JS fetch — exclude from CSRF
                .ignoringRequestMatchers("/api/**")
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/forecast", "/history", "/analytics", "/about", "/css/**", "/js/**", "/images/**", "/manifest.json").permitAll()
                .requestMatchers("/login", "/logout", "/error", "/api/**").permitAll()
                .anyRequest().permitAll()
            )
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            // Inject the custom cookie filter before standard authentication
            .addFilterBefore(cookieAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}