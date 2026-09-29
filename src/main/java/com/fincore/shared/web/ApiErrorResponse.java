package com.fincore.shared.web;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<String> errors
) {
    public static ApiErrorResponse of(int status, String code, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, code, message, path, List.of());
    }

    public static ApiErrorResponse of(int status, String code, String message, String path, List<String> errors) {
        return new ApiErrorResponse(Instant.now(), status, code, message, path, errors);
    }
}
