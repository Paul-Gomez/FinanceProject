package com.fincore.identity.infrastructure.security;

import com.fincore.identity.infrastructure.persistence.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

/**
 * Autentica la petición con el access token. Además de validar firma y caducidad comprueba que la
 * sesión del token siga activa: así un logout, revocar un dispositivo o cambiar la contraseña
 * cortan el acceso al momento, sin esperar a que caduque el token (hasta 15 minutos).
 * No es un @Component a propósito: Spring Boot registraría el filtro también en el contenedor
 * de servlets y se ejecutaría dos veces.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserSessionRepository sessionRepository;
    private final Clock clock;

    public JwtAuthenticationFilter(JwtService jwtService, UserSessionRepository sessionRepository, Clock clock) {
        this.jwtService = jwtService;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            jwtService.parse(header.substring(BEARER_PREFIX.length()))
                    .filter(user -> sessionRepository.isActive(user.getSessionId(), user.getUserId(), clock.instant()))
                    .ifPresent(user -> SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(user, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))));
        }
        chain.doFilter(request, response);
    }
}
