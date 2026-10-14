package com.fincore.identity.infrastructure.web;

import com.fincore.identity.application.SessionService;
import com.fincore.identity.infrastructure.security.CurrentSessionId;
import com.fincore.identity.infrastructure.security.CurrentUserId;
import com.fincore.identity.infrastructure.web.dto.SessionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Dispositivos con sesión abierta: el usuario puede verlos y cerrarlos. */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public List<SessionResponse> list(@CurrentUserId UUID userId, @CurrentSessionId UUID currentSessionId) {
        return sessionService.listActive(userId).stream()
                .map(s -> new SessionResponse(s.getId(), s.getDeviceName(), s.getUserAgent(), s.getIpAddress(),
                        s.getCreatedAt(), s.getLastUsedAt(), s.getExpiresAt(), s.getId().equals(currentSessionId)))
                .toList();
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> revoke(@CurrentUserId UUID userId, @PathVariable UUID sessionId) {
        sessionService.revokeOne(sessionId, userId);
        return ResponseEntity.noContent().build();
    }

    /** Cierra todas las sesiones menos la actual ("cerrar sesión en los demás dispositivos"). */
    @DeleteMapping
    public ResponseEntity<Void> revokeOthers(@CurrentUserId UUID userId, @CurrentSessionId UUID currentSessionId) {
        sessionService.revokeAllExcept(userId, currentSessionId, SessionService.REASON_REVOKED_BY_USER);
        return ResponseEntity.noContent().build();
    }
}
