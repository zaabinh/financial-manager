package com.example.financemanager.auth.api.dto.response;

public record TokenResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    Long expiresIn
) {
}
