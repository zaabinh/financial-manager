package com.example.financemanager.auth.infrastructure.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");
    private static final String SECRET =
            "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=";

    @Test
    void issuesAndParsesRequiredAccessTokenClaims() {
        JwtService service = service("issuer", "audience", NOW);
        UUID userId = UUID.randomUUID();

        JwtService.IssuedAccessToken issued = service.issue(userId, "USER");
        JwtService.AccessTokenClaims claims = service.parse(issued.value());

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.role()).isEqualTo("USER");
        assertThat(claims.tokenId()).isNotBlank();
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(issued.value().split("\\.")).hasSize(3);
    }

    @Test
    void rejectsExpiredAccessToken() {
        String token = service("issuer", "audience", NOW)
                .issue(UUID.randomUUID(), "USER")
                .value();

        JwtService verifier = service(
                "issuer",
                "audience",
                NOW.plus(Duration.ofMinutes(16))
        );
        assertThatThrownBy(() -> verifier.parse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsIncorrectAudience() {
        String token = service("issuer", "audience", NOW)
                .issue(UUID.randomUUID(), "USER")
                .value();

        assertThatThrownBy(() -> service("issuer", "other", NOW).parse(token))
                .isInstanceOf(IncorrectClaimException.class);
    }

    @Test
    void rejectsUnsupportedRoleAtIssuance() {
        assertThatThrownBy(() -> service("issuer", "audience", NOW)
                .issue(UUID.randomUUID(), "OWNER"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private JwtService service(String issuer, String audience, Instant instant) {
        JwtProperties properties = new JwtProperties(
                issuer,
                audience,
                SECRET,
                Duration.ofMinutes(15),
                Duration.ofDays(7)
        );
        return new JwtService(
                properties,
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }
}
