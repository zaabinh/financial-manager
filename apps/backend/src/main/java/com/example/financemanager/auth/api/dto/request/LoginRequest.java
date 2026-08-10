package com.example.financemanager.auth.api.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record LoginRequest (
        @NotBlank(message = "Username must not be blank")
        String username,
        @NotBlank(message = "Password must not be blank")
        String password,
        @Size(max = 100, message = "Device name must not exceed 100 characters")
        String deviceName
) {
}
