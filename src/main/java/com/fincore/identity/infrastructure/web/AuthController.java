package com.fincore.identity.infrastructure.web;

import com.fincore.identity.application.AuthService;
import com.fincore.identity.application.ClientInfo;
import com.fincore.identity.infrastructure.web.dto.LoginRequest;
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

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserMapper userMapper;

    public AuthController(AuthService authService, UserMapper userMapper) {
        this.authService = authService;
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

    static ClientInfo clientInfo(String deviceName, HttpServletRequest request) {
        return new ClientInfo(deviceName, request.getHeader("User-Agent"), request.getRemoteAddr());
    }
}
