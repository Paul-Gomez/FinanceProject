package com.fincore.identity.infrastructure.web;

import com.fincore.identity.application.AccountSecurityService;
import com.fincore.identity.infrastructure.security.CurrentSessionId;
import com.fincore.identity.infrastructure.security.CurrentUserId;
import com.fincore.identity.infrastructure.web.dto.ChangePasswordRequest;
import com.fincore.identity.infrastructure.web.dto.ForgotPasswordRequest;
import com.fincore.identity.infrastructure.web.dto.ResetPasswordRequest;
import com.fincore.identity.infrastructure.web.dto.VerifyEmailRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AccountSecurityController {

    private final AccountSecurityService accountSecurity;

    public AccountSecurityController(AccountSecurityService accountSecurity) {
        this.accountSecurity = accountSecurity;
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@CurrentUserId UUID userId, @CurrentSessionId UUID sessionId,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        accountSecurity.changePassword(userId, sessionId, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** 202 siempre, exista o no el email. */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        accountSecurity.requestPasswordReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accountSecurity.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        accountSecurity.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@CurrentUserId UUID userId) {
        accountSecurity.sendEmailVerification(userId);
        return ResponseEntity.accepted().build();
    }
}
