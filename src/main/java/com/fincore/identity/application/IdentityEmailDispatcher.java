package com.fincore.identity.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envía el email solo cuando la transacción que creó el token ya está confirmada. Si se enviara
 * dentro, un rollback posterior dejaría al usuario con un enlace a un token que no existe; y un
 * fallo del servidor de correo no debe deshacer el registro: el usuario puede pedir otro enlace.
 */
@Component
public class IdentityEmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(IdentityEmailDispatcher.class);

    private final IdentityMailer mailer;

    public IdentityEmailDispatcher(IdentityMailer mailer) {
        this.mailer = mailer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(IdentityEmailRequested event) {
        try {
            switch (event.purpose()) {
                case EMAIL_VERIFICATION -> mailer.sendEmailVerification(event.email(), event.displayName(), event.token());
                case PASSWORD_RESET -> mailer.sendPasswordReset(event.email(), event.displayName(), event.token());
            }
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el email de tipo {}", event.purpose(), e);
        }
    }
}
