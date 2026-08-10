package com.example.financemanager.auth.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest (
    @Email(message = "Email is invalid")
    @Size(max = 254, message = "Email must not exceed 254 characters")
    String email,
    @NotBlank(message = "Username must not be blank")
    @Size(min = 3, max = 50, message = "Username must contain between 3 and 50 characters")
    String username,
    @NotBlank(message = "Display name must not be blank")
    @Size(max = 150, message = "Display name must not exceed 150 characters")
    String displayName,
    @NotBlank(message = "Password must not be blank")
    @Size(min = 8, max = 72, message = "Password must contain between 8 and 72 characters")
    String password,
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be an uppercase ISO-4217 code")
    String currency,
    @Size(max = 100, message = "Timezone must not exceed 100 characters")
    String timezone
) {
}
