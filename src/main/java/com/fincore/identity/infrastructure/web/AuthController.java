package com.fincore.identity.infrastructure.web;

import com.fincore.identity.application.AuthService;
import com.fincore.identity.application.ClientInfo;
import com.fincore.identity.application.SessionService;
import com.fincore.identity.infrastructure.security.CurrentSessionId;
import com.fincore.identity.infrastructure.security.CurrentUserId;
import com.fincore.identity.infrastructure.web.dto.LoginRequest;
import com.fincore.identity.infrastructure.web.dto.RefreshRequest;
import com.fincore.identity.infrastructure.web.dto.RegisterRequest;
import com.fincore.identity.infrastructure.web.dto.TokenResponse;
import com.fincore.users.infrastructure.web.UserMapper;
import com.fincore.users.infrastructure.web.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;
    private final UserMapper userMapper;

    public AuthController(AuthService authService, SessionService sessionService, UserMapper userMapper) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.userMapper = userMapper;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse body = userMapper.toResponse(
                authService.register(request.email(), request.password(), request.displayName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return TokenResponse.from(authService.login(request.email(), request.password(),
                clientInfo(request.deviceName(), httpRequest)));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.from(sessionService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CurrentUserId UUID userId, @CurrentSessionId UUID sessionId) {
        sessionService.logout(sessionId, userId);
        return ResponseEntity.noContent().build();
    }

    static ClientInfo clientInfo(String deviceName, HttpServletRequest request) {
        return new ClientInfo(deviceName, request.getHeader("User-Agent"), request.getRemoteAddr());
    }
}
