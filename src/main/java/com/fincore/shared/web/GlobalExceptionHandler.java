package com.fincore.shared.web;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

/**
 * Traduce los errores a respuestas con el formato común. Los errores de negocio llegan todos
 * como BusinessException con un tipo y un código, así que este manejador no necesita conocer
 * las excepciones de cada módulo (shared no depende de los módulos, son ellos los que dependen de shared).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        HttpStatus status = toHttpStatus(ex.getType());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        ex.responseHeaders().forEach(response::header);
        return response.body(ApiErrorResponse.of(status.value(), ex.getCode(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR", "La petición no es válida",
                request.getRequestURI(), errors));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return badRequest("MISSING_HEADER", "Falta la cabecera " + ex.getHeaderName(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // El mensaje original de Jackson incluye nombres de clases internas: no se devuelve al cliente.
        return badRequest("MALFORMED_REQUEST",
                "El cuerpo de la petición no es un JSON válido o contiene valores no permitidos", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiErrorResponse.of(
                HttpStatus.PAYLOAD_TOO_LARGE.value(), "FILE_TOO_LARGE", "El fichero supera el tamaño máximo permitido",
                request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return badRequest("INVALID_PARAMETER", "El parámetro '%s' no tiene un valor válido".formatted(ex.getName()), request);
    }

    /**
     * Último recurso. Las excepciones web de Spring (405, 404 de ruta, parámetro obligatorio que
     * falta...) traen su propio código HTTP y se respetan; cualquier otra cosa es un fallo nuestro:
     * se registra entera en el log y al cliente solo le llega un mensaje genérico.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse springError) {
            HttpStatus status = HttpStatus.resolve(springError.getStatusCode().value());
            String code = status != null ? status.name() : "HTTP_" + springError.getStatusCode().value();
            return ResponseEntity.status(springError.getStatusCode()).body(ApiErrorResponse.of(
                    springError.getStatusCode().value(), code, springError.getBody().getDetail(), request.getRequestURI()));
        }

        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR",
                "Ha ocurrido un error inesperado", request.getRequestURI()));
    }

    private static ResponseEntity<ApiErrorResponse> badRequest(String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), code, message, request.getRequestURI()));
    }

    private static HttpStatus toHttpStatus(ErrorType type) {
        return switch (type) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
        };
    }
}
