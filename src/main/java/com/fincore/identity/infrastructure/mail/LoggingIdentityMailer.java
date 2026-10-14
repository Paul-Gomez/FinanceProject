package com.fincore.identity.infrastructure.mail;

import com.fincore.identity.application.IdentityMailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementación para desarrollo: no envía nada, solo deja constancia en el log de que se habría
 * enviado. A propósito no escribe el token ni el enlace: los logs no deben contener credenciales.
 * Para producción se sustituye por una implementación con SMTP o un proveedor de email.
 */
@Component
public class LoggingIdentityMailer implements IdentityMailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingIdentityMailer.class);

    @Override
    public void sendEmailVerification(String email, String displayName, String token) {
        log.info("Email de verificación preparado para {}", mask(email));
    }

    @Override
    public void sendPasswordReset(String email, String displayName, String token) {
        log.info("Email de recuperación de contraseña preparado para {}", mask(email));
    }

    static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" + email.substring(Math.max(at, 0)) : email.charAt(0) + "***" + email.substring(at);
    }
}
