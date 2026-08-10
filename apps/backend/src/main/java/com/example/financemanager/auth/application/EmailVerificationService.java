package com.example.financemanager.auth.application;

import com.example.financemanager.auth.api.dto.response.UserResponse;
import com.example.financemanager.auth.config.EmailVerificationProperties;
import com.example.financemanager.auth.domain.EmailVerificationTokenSecrets;
import com.example.financemanager.auth.infrastructure.persistence.entity.EmailVerificationToken;
import com.example.financemanager.auth.infrastructure.persistence.repository.EmailVerificationTokenRepository;
import com.example.financemanager.notification.application.MailService;
import com.example.financemanager.notification.domain.MailMessage;
import com.example.financemanager.shared.error.UnprocessableEntityException;
import com.example.financemanager.user.domain.UserStatus;
import com.example.financemanager.user.infrastructure.persistence.entity.User;
import com.example.financemanager.user.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailVerificationService.class);

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailVerificationTokenSecrets tokenSecrets;
    private final EmailVerificationProperties properties;
    private final AuthenticationRateLimiter rateLimiter;
    private final MailService mailService;
    private final Clock clock;

    @Transactional
    public void issueAndSend(User user) {
        if (user.getEmail() == null || user.isEmailVerified()) {
            return;
        }

        Instant now = clock.instant();
        tokenRepository.revokeActiveForUser(user.getId(), now);
        EmailVerificationTokenSecrets.GeneratedToken generated = tokenSecrets.generate();
        tokenRepository.save(EmailVerificationToken.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .tokenHash(generated.hash())
                .expiresAt(now.plus(properties.tokenExpiration()))
                .build());

        sendVerificationMail(user, generated.rawValue());
        LOGGER.info("authentication_event=email_verification_issued userId={}", user.getId());
    }

    @Transactional
    public void request(String email, ClientMetadata clientMetadata) {
        String normalizedEmail = email.trim();
        rateLimiter.checkAndRecordEmailVerification(normalizedEmail, clientMetadata);
        Optional<User> user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .filter(candidate -> !candidate.isEmailVerified());
        user.ifPresent(this::issueAndSend);
    }

    @Transactional
    public UserResponse confirm(String rawToken) {
        EmailVerificationToken token = tokenRepository
                .findByTokenHashForUpdate(tokenSecrets.hash(rawToken.trim()))
                .orElseThrow(EmailVerificationService::invalidToken);
        User user = userRepository.findByIdAndStatusAndDeletedAtIsNull(
                        token.getUserId(),
                        UserStatus.ACTIVE
                )
                .orElseThrow(EmailVerificationService::invalidToken);

        if (token.isUsed() && user.isEmailVerified()) {
            return UserResponse.from(user);
        }
        if (token.isRevoked() || !token.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw invalidToken();
        }
        if (token.isExpiredAt(clock.instant())) {
            throw new UnprocessableEntityException(
                    "EMAIL_VERIFICATION_TOKEN_EXPIRED",
                    "Email verification token has expired"
            );
        }

        Instant now = clock.instant();
        token.markUsed(now);
        user.markEmailVerified();
        tokenRepository.revokeActiveForUser(user.getId(), now);
        LOGGER.info("authentication_event=email_verified userId={}", user.getId());
        return UserResponse.from(user);
    }

    private void sendVerificationMail(User user, String rawToken) {
        String verificationUrl = UriComponentsBuilder
                .fromUriString(properties.clientPublicUrl())
                .queryParam("verificationToken", rawToken)
                .build()
                .toUriString();
        try {
            mailService.send(new MailMessage(
                    user.getEmail(),
                    "Verify your Personal Finance Manager email",
                    "verify-email",
                    Map.of(
                            "displayName", user.getDisplayName(),
                            "verificationUrl", verificationUrl,
                            "expirationHours", properties.tokenExpiration().toHours()
                    )
            ));
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "authentication_event=email_verification_delivery_failed userId={}",
                    user.getId(),
                    exception
            );
        }
    }

    private static UnprocessableEntityException invalidToken() {
        return new UnprocessableEntityException(
                "INVALID_EMAIL_VERIFICATION_TOKEN",
                "Email verification token is invalid"
        );
    }
}
