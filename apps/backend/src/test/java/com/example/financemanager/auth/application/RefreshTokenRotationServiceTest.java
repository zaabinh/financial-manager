package com.example.financemanager.auth.application;

import com.example.financemanager.auth.domain.RefreshTokenSecrets;
import com.example.financemanager.auth.infrastructure.persistence.entity.RefreshToken;
import com.example.financemanager.auth.infrastructure.persistence.repository.RefreshTokenRepository;
import com.example.financemanager.auth.infrastructure.security.JwtService;
import com.example.financemanager.user.domain.UserStatus;
import com.example.financemanager.user.infrastructure.persistence.entity.User;
import com.example.financemanager.user.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRotationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock UserRepository userRepository;
    @Mock RefreshTokenSecrets refreshTokenSecrets;
    @Mock JwtService jwtService;

    private RefreshTokenRotationService rotationService;

    @BeforeEach
    void setUp() {
        rotationService = new RefreshTokenRotationService(
                refreshTokenRepository,
                userRepository,
                refreshTokenSecrets,
                jwtService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        given(refreshTokenSecrets.hash(any())).willReturn("presented-hash");
    }

    @Test
    void rotatedTokenReuseRevokesEntireFamily() {
        RefreshToken current = token(NOW.plus(Duration.ofDays(1)));
        ReflectionTestUtils.setField(current, "replacedByTokenId", UUID.randomUUID());
        given(refreshTokenRepository.findByTokenHashForUpdate("presented-hash"))
                .willReturn(Optional.of(current));

        RefreshTokenRotationService.RotationResult result =
                rotationService.rotate("raw", new ClientMetadata(null, null));

        assertThat(result.status())
                .isEqualTo(RefreshTokenRotationService.RotationStatus.REUSED);
        verify(refreshTokenRepository).revokeFamily(
                current.getTokenFamilyId(),
                NOW,
                "REUSE_DETECTED"
        );
    }

    @Test
    void expiredTokenIsRevoked() {
        RefreshToken current = token(NOW.minusSeconds(1));
        given(refreshTokenRepository.findByTokenHashForUpdate("presented-hash"))
                .willReturn(Optional.of(current));

        RefreshTokenRotationService.RotationResult result =
                rotationService.rotate("raw", new ClientMetadata(null, null));

        assertThat(result.status())
                .isEqualTo(RefreshTokenRotationService.RotationStatus.EXPIRED);
        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getRevokeReason()).isEqualTo("EXPIRED");
    }

    @Test
    void validTokenRotatesToReplacementInSameFamily() {
        RefreshToken current = token(NOW.plus(Duration.ofDays(1)));
        User user = User.builder()
                .id(current.getUserId())
                .username("minh")
                .displayName("Minh")
                .passwordHash("hash")
                .build();
        UUID replacementId = UUID.randomUUID();
        given(refreshTokenRepository.findByTokenHashForUpdate("presented-hash"))
                .willReturn(Optional.of(current));
        given(userRepository.findByIdAndStatusAndDeletedAtIsNull(
                current.getUserId(),
                UserStatus.ACTIVE
        )).willReturn(Optional.of(user));
        given(refreshTokenSecrets.generate()).willReturn(
                new RefreshTokenSecrets.GeneratedRefreshToken("replacement-raw", "replacement-hash")
        );
        given(jwtService.refreshTokenExpiration()).willReturn(Duration.ofDays(7));
        given(refreshTokenRepository.saveAndFlush(any(RefreshToken.class)))
                .willAnswer(invocation -> {
                    RefreshToken replacement = invocation.getArgument(0);
                    ReflectionTestUtils.setField(replacement, "id", replacementId);
                    return replacement;
                });

        RefreshTokenRotationService.RotationResult result =
                rotationService.rotate("raw", new ClientMetadata(null, null));

        assertThat(result.status())
                .isEqualTo(RefreshTokenRotationService.RotationStatus.SUCCESS);
        assertThat(result.rawRefreshToken()).isEqualTo("replacement-raw");
        assertThat(current.getReplacedByTokenId()).isEqualTo(replacementId);
        assertThat(current.getRevokeReason()).isEqualTo("ROTATED");
    }

    private RefreshToken token(Instant expiresAt) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("presented-hash")
                .tokenFamilyId(UUID.randomUUID())
                .expiresAt(expiresAt)
                .build();
    }
}
