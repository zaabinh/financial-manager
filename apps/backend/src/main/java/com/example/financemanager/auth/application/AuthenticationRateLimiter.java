package com.example.financemanager.auth.application;

import com.example.financemanager.auth.exception.AuthRateLimitExceededException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthenticationRateLimiter {

    private static final int LOGIN_FAILURE_LIMIT = 5;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final int REFRESH_ATTEMPT_LIMIT = 20;
    private static final Duration REFRESH_WINDOW = Duration.ofMinutes(1);
    private static final int EMAIL_VERIFICATION_LIMIT = 3;
    private static final Duration EMAIL_VERIFICATION_WINDOW = Duration.ofHours(1);
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final ConcurrentHashMap<String, Window> loginFailures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> refreshAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> emailVerificationAttempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public AuthenticationRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void checkLoginAllowed(String username, ClientMetadata clientMetadata) {
        Window window = loginFailures.get(loginKey(username, clientMetadata));
        if (window != null
                && !window.isExpiredAt(clock.instant())
                && window.count() >= LOGIN_FAILURE_LIMIT) {
            throw new AuthRateLimitExceededException();
        }
    }

    public void recordLoginFailure(String username, ClientMetadata clientMetadata) {
        increment(
                loginFailures,
                loginKey(username, clientMetadata),
                LOGIN_WINDOW
        );
    }

    public void clearLoginFailures(String username, ClientMetadata clientMetadata) {
        loginFailures.remove(loginKey(username, clientMetadata));
    }

    public void checkAndRecordRefresh(ClientMetadata clientMetadata) {
        Window window = increment(
                refreshAttempts,
                address(clientMetadata),
                REFRESH_WINDOW
        );
        if (window.count() > REFRESH_ATTEMPT_LIMIT) {
            throw new AuthRateLimitExceededException();
        }
    }

    public void checkAndRecordEmailVerification(
            String email,
            ClientMetadata clientMetadata
    ) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Window window = increment(
                emailVerificationAttempts,
                hash(normalizedEmail) + ':' + address(clientMetadata),
                EMAIL_VERIFICATION_WINDOW
        );
        if (window.count() > EMAIL_VERIFICATION_LIMIT) {
            throw new AuthRateLimitExceededException();
        }
    }

    private Window increment(
            ConcurrentHashMap<String, Window> windows,
            String key,
            Duration duration
    ) {
        Instant now = clock.instant();
        windows.entrySet().removeIf(entry -> entry.getValue().isExpiredAt(now));
        if (!windows.containsKey(key) && windows.size() >= MAX_TRACKED_KEYS) {
            throw new AuthRateLimitExceededException();
        }
        return windows.compute(key, (ignored, current) -> {
            if (current == null || current.isExpiredAt(now)) {
                return new Window(1, now.plus(duration));
            }
            return new Window(current.count() + 1, current.expiresAt());
        });
    }

    private String loginKey(String username, ClientMetadata clientMetadata) {
        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        return hash(normalizedUsername) + ':' + address(clientMetadata);
    }

    private String address(ClientMetadata clientMetadata) {
        return clientMetadata.ipAddress() == null
                ? "unknown"
                : clientMetadata.ipAddress().getHostAddress();
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Window(int count, Instant expiresAt) {
        boolean isExpiredAt(Instant instant) {
            return !expiresAt.isAfter(instant);
        }
    }
}
