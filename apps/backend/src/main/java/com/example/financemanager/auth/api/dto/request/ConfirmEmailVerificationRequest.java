package com.example.financemanager.auth.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmEmailVerificationRequest(
        @NotBlank(message = "Verification token must not be blank")
        @Size(max = 200, message = "Verification token is invalid")
        String token
) {
}
