package com.fincore.shared.web;

import com.fincore.accounts.application.AccountAccessDeniedException;
import com.fincore.accounts.application.AccountNameAlreadyExistsException;
import com.fincore.accounts.application.AccountNotFoundException;
import com.fincore.accounts.domain.AccountBalanceNotZeroException;
import com.fincore.accounts.domain.AccountNotActiveException;
import com.fincore.accounts.domain.InvalidAccountStatusTransitionException;
import com.fincore.identity.application.EmailAlreadyRegisteredException;
import com.fincore.identity.application.InvalidCredentialsException;
import com.fincore.identity.application.InvalidPasswordException;
import com.fincore.users.application.UserNotFoundException;
import com.fincore.categories.application.CategoryAccessDeniedException;
import com.fincore.categories.application.CategoryAlreadyExistsException;
import com.fincore.categories.application.CategoryInUseException;
import com.fincore.categories.application.CategoryKindMismatchException;
import com.fincore.categories.application.CategoryNotFoundException;
import com.fincore.categories.application.CategoryReadOnlyException;
import com.fincore.categories.application.InvalidCategoryParentException;
import com.fincore.shared.idempotency.IdempotencyKeyReusedException;
import com.fincore.shared.idempotency.InvalidIdempotencyKeyException;
import com.fincore.shared.money.CurrencyMismatchException;
import com.fincore.shared.money.InvalidAmountPrecisionException;
import com.fincore.shared.money.InvalidCurrencyException;
import com.fincore.transactions.application.SameAccountTransferException;
import com.fincore.transactions.application.UnsupportedTransactionTypeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "MISSING_HEADER",
                "Falta la cabecera " + ex.getHeaderName(), request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        // El mensaje original de Jackson incluye nombres de clases internas: no se devuelve al cliente.
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "MALFORMED_REQUEST",
                "El cuerpo de la petición no es un JSON válido o contiene valores no permitidos", request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "INVALID_PARAMETER",
                "El parámetro '%s' no tiene un valor válido".formatted(ex.getName()), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(), "INVALID_CREDENTIALS", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
                HttpStatus.CONFLICT.value(), "EMAIL_ALREADY_REGISTERED", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidPassword(InvalidPasswordException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "INVALID_PASSWORD", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.of(
                HttpStatus.NOT_FOUND.value(), "USER_NOT_FOUND", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountNotFound(AccountNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.of(
                HttpStatus.NOT_FOUND.value(), "ACCOUNT_NOT_FOUND", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(AccountAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccountAccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiErrorResponse.of(
                HttpStatus.FORBIDDEN.value(), "ACCESS_DENIED", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(AccountNameAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountNameTaken(AccountNameAlreadyExistsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
                HttpStatus.CONFLICT.value(), "ACCOUNT_NAME_ALREADY_EXISTS", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(AccountBalanceNotZeroException.class)
    public ResponseEntity<ApiErrorResponse> handleBalanceNotZero(AccountBalanceNotZeroException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
                HttpStatus.CONFLICT.value(), "ACCOUNT_BALANCE_NOT_ZERO", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountNotActive(AccountNotActiveException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
                HttpStatus.CONFLICT.value(), "ACCOUNT_NOT_ACTIVE", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleCurrencyMismatch(CurrencyMismatchException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiErrorResponse.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(), "CURRENCY_MISMATCH", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoryNotFound(CategoryNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.of(
                HttpStatus.NOT_FOUND.value(), "CATEGORY_NOT_FOUND", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(CategoryAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoryAccessDenied(CategoryAccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiErrorResponse.of(
                HttpStatus.FORBIDDEN.value(), "ACCESS_DENIED", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(CategoryReadOnlyException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoryReadOnly(CategoryReadOnlyException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiErrorResponse.of(
                HttpStatus.FORBIDDEN.value(), "CATEGORY_READ_ONLY", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(CategoryKindMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoryKindMismatch(CategoryKindMismatchException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiErrorResponse.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(), "CATEGORY_KIND_MISMATCH", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidCategoryParentException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCategoryParent(InvalidCategoryParentException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiErrorResponse.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(), "INVALID_CATEGORY_PARENT", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(CategoryInUseException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoryInUse(CategoryInUseException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
                HttpStatus.CONFLICT.value(), "CATEGORY_IN_USE", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(CategoryAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoryAlreadyExists(CategoryAlreadyExistsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
                HttpStatus.CONFLICT.value(), "CATEGORY_ALREADY_EXISTS", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyKeyReused(IdempotencyKeyReusedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiErrorResponse.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(), "IDEMPOTENCY_KEY_REUSED", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidIdempotencyKey(InvalidIdempotencyKeyException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "INVALID_IDEMPOTENCY_KEY", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(SameAccountTransferException.class)
    public ResponseEntity<ApiErrorResponse> handleSameAccountTransfer(SameAccountTransferException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "SAME_ACCOUNT_TRANSFER", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidAmountPrecisionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidAmountPrecision(InvalidAmountPrecisionException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "INVALID_AMOUNT_PRECISION", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidCurrencyException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCurrency(InvalidCurrencyException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "INVALID_CURRENCY", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(UnsupportedTransactionTypeException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedType(UnsupportedTransactionTypeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "UNSUPPORTED_TRANSACTION_TYPE", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidAccountStatusTransitionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidStatusTransition(InvalidAccountStatusTransitionException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
                HttpStatus.CONFLICT.value(), "INVALID_STATUS_TRANSITION", ex.getMessage(), request.getRequestURI()));
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
}
