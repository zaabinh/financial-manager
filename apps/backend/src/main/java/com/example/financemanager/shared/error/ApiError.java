package com.example.financemanager.shared.error;

import java.util.List;

public record ApiError(
        String code,
        String message,
        List<FieldErrorResponse> fieldErrors
) {
}
