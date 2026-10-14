package com.fincore.identity.application;

/**
 * Puerto de salida para los emails de identidad. El módulo no sabe si detrás hay SMTP, un
 * proveedor como SES o (en local) un simple log: basta con añadir otra implementación.
 */
public interface IdentityMailer {

    void sendEmailVerification(String email, String displayName, String token);

    void sendPasswordReset(String email, String displayName, String token);
}
