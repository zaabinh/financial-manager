package com.example.financemanager.auth.api.dto.request;
import jakarta.validation.constraints.NotBlank;


public record LoginRequest (
        @NotBlank(message = "Email or username must not be blank")
        String identifier,
        @NotBlank(message = "Password must not be blank")
        String password
) {

}
