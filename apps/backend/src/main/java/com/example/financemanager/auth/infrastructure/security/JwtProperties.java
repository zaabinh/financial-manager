package com.example.financemanager.auth.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotBlank String secret,
        @NotNull Duration accessTokenExpiration,
        @NotNull Duration refreshTokenExpiration
) {
    public JwtProperties {
        if (accessTokenExpiration != null &&
                (accessTokenExpiration.isZero() || accessTokenExpiration.isNegative())) {
            throw new IllegalArgumentException("Access-token expiration must be positive");
        }

        if (refreshTokenExpiration != null &&
                (refreshTokenExpiration.isZero() || refreshTokenExpiration.isNegative())) {
            throw new IllegalArgumentException("Refresh-token expiration must be positive");
        }
    }
}
