package com.example.financemanager.auth.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest (
    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email is invalid")
    String email,
    @NotBlank(message = "Username must not be blank")
    String username,
    @NotBlank(message = "Password must not be blank")
    @Size(min = 8, message = "Password must be longer than 8 characters")
    String password
) {
    
}
