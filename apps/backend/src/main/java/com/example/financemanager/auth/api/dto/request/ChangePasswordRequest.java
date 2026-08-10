package com.example.financemanager.auth.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password must not be blank")
        String currentPassword,
        @NotBlank(message = "New password must not be blank")
        @Size(min = 12, max = 72, message = "New password must contain between 12 and 72 characters")
        String newPassword
) {
}
