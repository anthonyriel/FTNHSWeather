package edu.ftnhs.weather_manager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/forecast", "/history", "/analytics", "/about", "/css/**", "/js/**", "/images/**", "/manifest.json").permitAll()
                .requestMatchers("/login", "/logout", "/error", "/api/**").permitAll()
                .anyRequest().permitAll()
            )
            .formLogin(form -> form.disable()) // Disable Spring Security form login so custom DashboardController handles it
            .logout(logout -> logout.disable()); // Disable Spring Security logout so custom DashboardController handles cookies

        return http.build();
    }
}