package com.fincore.shared.web;

import com.fincore.accounts.application.AccountAccessDeniedException;
import com.fincore.accounts.application.AccountNotFoundException;
import com.fincore.accounts.application.UnknownOwnerException;
import com.fincore.accounts.domain.AccountNotActiveException;
import com.fincore.accounts.domain.InvalidAccountStatusTransitionException;
import com.fincore.shared.money.CurrencyMismatchException;
import com.fincore.transactions.application.UnsupportedTransactionTypeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR", "La petición no es válida",
                request.getRequestURI(), errors));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountNotFound(AccountNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorResponse.of(
                HttpStatus.NOT_FOUND.value(), "ACCOUNT_NOT_FOUND", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(UnknownOwnerException.class)
    public ResponseEntity<ApiErrorResponse> handleUnknownOwner(UnknownOwnerException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), "UNKNOWN_OWNER", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(AccountAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccountAccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiErrorResponse.of(
                HttpStatus.FORBIDDEN.value(), "ACCESS_DENIED", ex.getMessage(), request.getRequestURI()));
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
}
