package com.example.financemanager.auth.api.dto.response;

public record LoginResponse(
    TokenResponse token,
    UserResponse user
) {

} 
