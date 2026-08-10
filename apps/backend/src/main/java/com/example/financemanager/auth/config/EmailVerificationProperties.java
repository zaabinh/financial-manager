package com.example.financemanager.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.auth.email-verification")
public record EmailVerificationProperties(
        @NotNull Duration tokenExpiration,
        @NotBlank String clientPublicUrl
) {
    public EmailVerificationProperties {
        if (tokenExpiration == null || tokenExpiration.isZero() || tokenExpiration.isNegative()) {
            throw new IllegalArgumentException("Email verification expiration must be positive");
        }
        if (clientPublicUrl == null || clientPublicUrl.isBlank()) {
            throw new IllegalArgumentException("Client public URL must not be blank");
        }
        URI uri = URI.create(clientPublicUrl);
        if (!uri.isAbsolute() || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
            throw new IllegalArgumentException("Client public URL must be an absolute HTTP(S) URL");
        }
    }
}
