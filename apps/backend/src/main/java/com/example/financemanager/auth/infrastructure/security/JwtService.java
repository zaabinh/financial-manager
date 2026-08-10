package com.example.financemanager.auth.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtService {

    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "ADMIN");

    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAccessToken issue(UUID userId, String role) {
        if (!ALLOWED_ROLES.contains(role)) {
            throw new IllegalArgumentException("Unsupported user role");
        }

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenExpiration());

        String value = Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .audience().add(properties.audience()).and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(properties.signingKey(), Jwts.SIG.HS256)
                .compact();

        return new IssuedAccessToken(value, expiresAt);
    }

    public AccessTokenClaims parse(String token) {
        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(properties.signingKey())
                .requireIssuer(properties.issuer())
                .requireAudience(properties.audience())
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token);

        if (!Jwts.SIG.HS256.getId().equals(parsed.getHeader().getAlgorithm())) {
            throw new UnsupportedJwtException("Unsupported JWT algorithm");
        }

        Claims claims = parsed.getPayload();
        String role = claims.get("role", String.class);
        String tokenId = claims.getId();
        if (!ALLOWED_ROLES.contains(role)) {
            throw new UnsupportedJwtException("Invalid role claim");
        }
        if (tokenId == null || tokenId.isBlank()) {
            throw new UnsupportedJwtException("Missing token identifier");
        }

        return new AccessTokenClaims(
                UUID.fromString(claims.getSubject()),
                role,
                tokenId,
                claims.getExpiration().toInstant()
        );
    }

    public Duration accessTokenExpiration() {
        return properties.accessTokenExpiration();
    }

    public Duration refreshTokenExpiration() {
        return properties.refreshTokenExpiration();
    }

    public record IssuedAccessToken(String value, Instant expiresAt) {
    }

    public record AccessTokenClaims(
            UUID userId,
            String role,
            String tokenId,
            Instant expiresAt
    ) {
    }
}
