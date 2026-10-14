package com.fincore.shared.error;

/**
 * Qué clase de error es, sin hablar de HTTP: el dominio dice "no existe" o "entra en conflicto"
 * y es la capa web la que decide que eso se traduce en un 404 o un 409.
 */
public enum ErrorType {
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    UNPROCESSABLE,
    TOO_MANY_REQUESTS
}
