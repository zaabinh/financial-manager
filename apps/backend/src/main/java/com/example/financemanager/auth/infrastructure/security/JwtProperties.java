package com.example.financemanager.auth.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.time.Duration;

import javax.crypto.SecretKey;

@ConfigurationProperties(prefix = "app.jwt")
@Validated
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotBlank String secret,
        @NotNull Duration accessTokenExpiration,
        @NotNull Duration refreshTokenExpiration
) {

    public JwtProperties {
        validateSecret(secret);

        if (accessTokenExpiration.isZero() || accessTokenExpiration.isNegative()) {
            throw new IllegalArgumentException("Access-token expiration must be positive");
        }

        if (refreshTokenExpiration.isZero() || refreshTokenExpiration.isNegative()) {
            throw new IllegalArgumentException("Refresh-token expiration must be positive");
        }
    }

    private static void validateSecret(String secret) {
        byte[] key;

        try {
            key = Decoders.BASE64.decode(secret);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JWT signing secret must be valid Base64", ex);
        }

        if (key.length < 32) {
            throw new IllegalArgumentException(
                    "JWT signing key must contain at least 32 bytes"
            );
        }
    }

    public SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
