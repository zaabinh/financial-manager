package com.example.financemanager.shared.error;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        boolean success,
        ApiError error,
        Instant timestamp,
        String path,
        String correlationId
) {
    public static ApiErrorResponse of(
            String code,
            String message,
            List<FieldErrorResponse> fieldErrors,
            String path,
            String correlationId
    ) {
        return new ApiErrorResponse(
                false,
                new ApiError(code, message, fieldErrors),
                Instant.now(),
                path,
                correlationId
        );
    }
}
