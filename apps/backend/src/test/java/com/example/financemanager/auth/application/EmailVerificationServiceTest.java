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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Mock UserRepository userRepository;
    @Mock EmailVerificationTokenRepository tokenRepository;
    @Mock EmailVerificationTokenSecrets tokenSecrets;
    @Mock AuthenticationRateLimiter rateLimiter;
    @Mock MailService mailService;
    @Mock Clock clock;

    private final EmailVerificationProperties properties =
            new EmailVerificationProperties(Duration.ofHours(24), "http://localhost:8081");

    @Test
    void issueStoresOnlyHashAndSendsRawTokenInClientLink() {
        EmailVerificationService service = service();
        User user = unverifiedUser();
        given(clock.instant()).willReturn(NOW);
        given(tokenSecrets.generate()).willReturn(
                new EmailVerificationTokenSecrets.GeneratedToken("raw-token", "token-hash")
        );

        service.issueAndSend(user);

        ArgumentCaptor<EmailVerificationToken> tokenCaptor =
                ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getTokenHash()).isEqualTo("token-hash");
        assertThat(tokenCaptor.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));

        ArgumentCaptor<MailMessage> mailCaptor = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailService).send(mailCaptor.capture());
        assertThat(mailCaptor.getValue().templateName()).isEqualTo("verify-email");
        assertThat(mailCaptor.getValue().templateVariables().get("verificationUrl").toString())
                .contains("verificationToken=raw-token")
                .doesNotContain("token-hash");
    }

    @Test
    void confirmMarksMatchingActiveTokenAndUserVerified() {
        EmailVerificationService service = service();
        User user = unverifiedUser();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .tokenHash("token-hash")
                .expiresAt(NOW.plus(Duration.ofHours(1)))
                .build();
        given(tokenSecrets.hash("raw-token")).willReturn("token-hash");
        given(tokenRepository.findByTokenHashForUpdate("token-hash"))
                .willReturn(Optional.of(token));
        given(userRepository.findByIdAndStatusAndDeletedAtIsNull(
                user.getId(), UserStatus.ACTIVE
        )).willReturn(Optional.of(user));
        given(clock.instant()).willReturn(NOW);

        UserResponse response = service.confirm("raw-token");

        assertThat(response.emailVerified()).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.getUsedAt()).isEqualTo(NOW);
        verify(tokenRepository).revokeActiveForUser(user.getId(), NOW);
    }

    @Test
    void confirmRejectsExpiredToken() {
        EmailVerificationService service = service();
        User user = unverifiedUser();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .tokenHash("token-hash")
                .expiresAt(NOW.minusSeconds(1))
                .build();
        given(tokenSecrets.hash("raw-token")).willReturn("token-hash");
        given(tokenRepository.findByTokenHashForUpdate("token-hash"))
                .willReturn(Optional.of(token));
        given(userRepository.findByIdAndStatusAndDeletedAtIsNull(
                user.getId(), UserStatus.ACTIVE
        )).willReturn(Optional.of(user));
        given(clock.instant()).willReturn(NOW);

        assertThatThrownBy(() -> service.confirm("raw-token"))
                .isInstanceOfSatisfying(UnprocessableEntityException.class, exception ->
                        assertThat(exception.code()).isEqualTo("EMAIL_VERIFICATION_TOKEN_EXPIRED")
                );
    }

    @Test
    void unknownResendEmailReturnsWithoutSending() {
        EmailVerificationService service = service();
        given(userRepository.findByEmailIgnoreCase("unknown@example.com"))
                .willReturn(Optional.empty());

        service.request("unknown@example.com", new ClientMetadata(null, null));

        verify(mailService, never()).send(any());
    }

    private EmailVerificationService service() {
        return new EmailVerificationService(
                userRepository,
                tokenRepository,
                tokenSecrets,
                properties,
                rateLimiter,
                mailService,
                clock
        );
    }

    private User unverifiedUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .username("minh.nguyen")
                .email("minh@example.com")
                .displayName("Minh Nguyen")
                .passwordHash("hash")
                .build();
    }
}
