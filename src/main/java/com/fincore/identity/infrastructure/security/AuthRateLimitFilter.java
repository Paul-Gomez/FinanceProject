package com.fincore.identity.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.shared.web.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Limita por IP los POST a /api/v1/auth/** (login, registro, refresh, recuperar contraseña...).
 * Complementa al bloqueo por cuenta: este frena a quien prueba muchas cuentas distintas desde
 * la misma IP, cosa que el bloqueo por cuenta no ve.
 * Usa getRemoteAddr(): detrás de un proxy habría que configurar server.forward-headers-strategy
 * para que sea la IP real del cliente y no la del proxy (y no fiarse de X-Forwarded-For a ciegas).
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final FixedWindowRateLimiter limiter;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(FixedWindowRateLimiter limiter, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && request.getRequestURI().startsWith("/api/v1/auth/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        FixedWindowRateLimiter.Decision decision = limiter.tryAcquire(request.getRemoteAddr());
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMITED", "Demasiadas peticiones; vuelve a intentarlo en unos segundos", request.getRequestURI()));
    }
}
