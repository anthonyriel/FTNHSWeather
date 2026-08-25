package edu.ftnhs.weather_manager.config;

import edu.ftnhs.weather_manager.entity.User;
import edu.ftnhs.weather_manager.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;

@Component
public class CookieAuthenticationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public CookieAuthenticationFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("adminUser".equals(cookie.getName())) {
                    String username = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
                    Optional<User> userOpt = userRepository.findByUsername(username);
                    
                    if (userOpt.isPresent() && Boolean.TRUE.equals(userOpt.get().getIsActive())) {
                        User user = userOpt.get();
                        String role = user.getRole() != null ? user.getRole().toUpperCase() : "USER";
                        
                        // Ensures Spring Security matches @PreAuthorize("hasRole('ADMIN')")
                        if (!role.startsWith("ROLE_")) {
                            role = "ROLE_" + role;
                        }
                        
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                user.getUsername(), null, Collections.singletonList(new SimpleGrantedAuthority(role))
                        );
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}