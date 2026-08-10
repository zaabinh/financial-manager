package com.example.financemanager.auth.application;

import com.example.financemanager.auth.exception.AuthRateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticationRateLimiterTest {

    private final MutableClock clock = new MutableClock(
            Instant.parse("2026-08-09T00:00:00Z")
    );
    private final AuthenticationRateLimiter rateLimiter =
            new AuthenticationRateLimiter(clock);
    private final ClientMetadata client = ClientMetadata.of(
            "test-device",
            "test-agent",
            InetAddress.getLoopbackAddress().getHostAddress()
    );

    @Test
    void blocksAfterFiveLoginFailuresAndResetsAfterWindow() {
        for (int attempt = 0; attempt < 5; attempt++) {
            rateLimiter.checkLoginAllowed("minh", client);
            rateLimiter.recordLoginFailure("minh", client);
        }

        assertThatThrownBy(() -> rateLimiter.checkLoginAllowed("minh", client))
                .isInstanceOf(AuthRateLimitExceededException.class);

        clock.advance(Duration.ofMinutes(15));
        assertThatCode(() -> rateLimiter.checkLoginAllowed("minh", client))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksTwentyFirstRefreshAttemptWithinOneMinute() {
        for (int attempt = 0; attempt < 20; attempt++) {
            rateLimiter.checkAndRecordRefresh(client);
        }

        assertThatThrownBy(() -> rateLimiter.checkAndRecordRefresh(client))
                .isInstanceOf(AuthRateLimitExceededException.class);

        clock.advance(Duration.ofMinutes(1));
        assertThatCode(() -> rateLimiter.checkAndRecordRefresh(client))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksFourthVerificationRequestWithinOneHour() {
        for (int attempt = 0; attempt < 3; attempt++) {
            rateLimiter.checkAndRecordEmailVerification("minh@example.com", client);
        }

        assertThatThrownBy(() -> rateLimiter.checkAndRecordEmailVerification(
                "MINH@example.com",
                client
        )).isInstanceOf(AuthRateLimitExceededException.class);

        clock.advance(Duration.ofHours(1));
        assertThatCode(() -> rateLimiter.checkAndRecordEmailVerification(
                "minh@example.com",
                client
        )).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
