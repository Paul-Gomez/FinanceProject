package com.fincore.shared.error;

import java.util.Map;

/** Base de los errores de negocio: llevan un tipo y un código estable que ve el cliente de la API. */
public abstract class BusinessException extends RuntimeException {

    private final ErrorType type;
    private final String code;

    protected BusinessException(ErrorType type, String code, String message) {
        super(message);
        this.type = type;
        this.code = code;
    }

    public ErrorType getType() {
        return type;
    }

    public String getCode() {
        return code;
    }

    /** Cabeceras extra para la respuesta (por ejemplo Retry-After). */
    public Map<String, String> responseHeaders() {
        return Map.of();
    }
}
