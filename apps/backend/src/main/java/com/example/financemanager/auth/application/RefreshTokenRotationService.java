package com.example.financemanager.auth.application;

import com.example.financemanager.auth.domain.RefreshTokenSecrets;
import com.example.financemanager.auth.infrastructure.persistence.entity.RefreshToken;
import com.example.financemanager.auth.infrastructure.persistence.repository.RefreshTokenRepository;
import com.example.financemanager.auth.infrastructure.security.JwtService;
import com.example.financemanager.user.domain.UserStatus;
import com.example.financemanager.user.infrastructure.persistence.entity.User;
import com.example.financemanager.user.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RefreshTokenRotationService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RefreshTokenRotationService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenSecrets refreshTokenSecrets;
    private final JwtService jwtService;
    private final Clock clock;

    @Transactional
    public RotationResult rotate(String rawRefreshToken, ClientMetadata clientMetadata) {
        String tokenHash = refreshTokenSecrets.hash(rawRefreshToken);
        RefreshToken current = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElse(null);

        if (current == null) {
            return RotationResult.failure(RotationStatus.UNKNOWN);
        }
        Instant now = clock.instant();
        if (current.wasRotated()) {
            refreshTokenRepository.revokeFamily(
                    current.getTokenFamilyId(),
                    now,
                    "REUSE_DETECTED"
            );
            LOGGER.warn(
                    "authentication_event=refresh_token_reuse userId={} familyId={}",
                    current.getUserId(),
                    current.getTokenFamilyId()
            );
            return RotationResult.failure(RotationStatus.REUSED);
        }
        if (current.isRevoked()) {
            return RotationResult.failure(RotationStatus.REVOKED);
        }
        if (current.isExpiredAt(now)) {
            current.revoke(now, "EXPIRED");
            return RotationResult.failure(RotationStatus.EXPIRED);
        }

        User user = userRepository
                .findByIdAndStatusAndDeletedAtIsNull(current.getUserId(), UserStatus.ACTIVE)
                .orElse(null);
        if (user == null) {
            refreshTokenRepository.revokeFamily(
                    current.getTokenFamilyId(),
                    now,
                    "USER_INACTIVE"
            );
            return RotationResult.failure(RotationStatus.INACTIVE_USER);
        }

        RefreshTokenSecrets.GeneratedRefreshToken generated =
                refreshTokenSecrets.generate();
        RefreshToken replacement = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(generated.hash())
                .tokenFamilyId(current.getTokenFamilyId())
                .expiresAt(now.plus(jwtService.refreshTokenExpiration()))
                .userAgent(clientMetadata.userAgent())
                .ipAddress(clientMetadata.ipAddress())
                .build();

        refreshTokenRepository.saveAndFlush(replacement);
        current.rotateTo(replacement.getId(), now);

        return RotationResult.success(user, generated.rawValue());
    }

    public enum RotationStatus {
        SUCCESS,
        UNKNOWN,
        EXPIRED,
        REVOKED,
        REUSED,
        INACTIVE_USER
    }

    public record RotationResult(
            RotationStatus status,
            User user,
            String rawRefreshToken
    ) {
        public static RotationResult success(User user, String rawRefreshToken) {
            return new RotationResult(RotationStatus.SUCCESS, user, rawRefreshToken);
        }

        public static RotationResult failure(RotationStatus status) {
            return new RotationResult(status, null, null);
        }
    }
}
