package com.fincore.support;

import com.fincore.identity.application.IdentityMailer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** En tests sustituye al envío real y guarda el último token enviado a cada email. */
public class CapturingIdentityMailer implements IdentityMailer {

    private final Map<String, String> verificationTokens = new ConcurrentHashMap<>();
    private final Map<String, String> resetTokens = new ConcurrentHashMap<>();

    @Override
    public void sendEmailVerification(String email, String displayName, String token) {
        verificationTokens.put(email, token);
    }

    @Override
    public void sendPasswordReset(String email, String displayName, String token) {
        resetTokens.put(email, token);
    }

    public Optional<String> lastVerificationToken(String email) {
        return Optional.ofNullable(verificationTokens.get(email));
    }

    public Optional<String> lastResetToken(String email) {
        return Optional.ofNullable(resetTokens.get(email));
    }
}
