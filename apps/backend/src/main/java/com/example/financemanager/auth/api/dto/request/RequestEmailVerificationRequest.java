package com.example.financemanager.auth.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestEmailVerificationRequest(
        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email is invalid")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email
) {
}
