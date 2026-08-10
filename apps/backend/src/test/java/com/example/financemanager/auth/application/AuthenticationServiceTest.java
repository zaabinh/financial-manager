package com.example.financemanager.auth.application;

import com.example.financemanager.auth.api.dto.request.LoginRequest;
import com.example.financemanager.auth.api.dto.request.RegisterRequest;
import com.example.financemanager.auth.api.dto.response.TokenResponse;
import com.example.financemanager.auth.domain.PasswordPolicy;
import com.example.financemanager.auth.domain.RefreshTokenSecrets;
import com.example.financemanager.auth.infrastructure.persistence.entity.RefreshToken;
import com.example.financemanager.auth.infrastructure.persistence.repository.RefreshTokenRepository;
import com.example.financemanager.auth.infrastructure.security.JwtService;
import com.example.financemanager.auth.infrastructure.security.UserPrincipal;
import com.example.financemanager.shared.error.UnauthorizedException;
import com.example.financemanager.shared.error.ForbiddenException;
import com.example.financemanager.user.domain.UserStatus;
import com.example.financemanager.user.infrastructure.persistence.entity.User;
import com.example.financemanager.user.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PasswordPolicy passwordPolicy;
    @Mock JwtService jwtService;
    @Mock RefreshTokenSecrets refreshTokenSecrets;
    @Mock AuthenticationManager authenticationManager;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock RefreshTokenRotationService refreshTokenRotationService;
    @Mock AuthenticationRateLimiter authenticationRateLimiter;
    @Mock EmailVerificationService emailVerificationService;
    @Mock Clock clock;

    @InjectMocks AuthenticationService authenticationService;

    @Test
    void registrationPopulatesRequiredUserFields() {
        RegisterRequest request = new RegisterRequest(
                null,
                "minh.nguyen",
                "Minh Nguyen",
                "StrongPassword#2026",
                null,
                null
        );
        given(passwordEncoder.encode(request.password())).willReturn("bcrypt-hash");
        given(userRepository.saveAndFlush(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        authenticationService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("minh.nguyen");
        assertThat(saved.getDisplayName()).isEqualTo("Minh Nguyen");
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getCurrency()).isEqualTo("VND");
        assertThat(saved.getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(saved.getPasswordHash()).isEqualTo("bcrypt-hash");
        verify(passwordPolicy).validate(request.password(), request.username());
        verify(emailVerificationService).issueAndSend(saved);
    }

    @Test
    void loginRejectsCorrectCredentialsUntilEmailIsVerified() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("minh.nguyen")
                .email("minh@example.com")
                .displayName("Minh Nguyen")
                .passwordHash("hash")
                .build();
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        given(authentication.getPrincipal()).willReturn(new UserPrincipal(user));
        given(authenticationManager.authenticate(any(Authentication.class)))
                .willReturn(authentication);

        assertThatThrownBy(() -> authenticationService.login(
                new LoginRequest("minh.nguyen", "StrongPassword#2026", "phone"),
                new ClientMetadata(null, null)
        )).isInstanceOfSatisfying(ForbiddenException.class, exception ->
                assertThat(exception.code()).isEqualTo("EMAIL_NOT_VERIFIED")
        );
    }

    @Test
    void loginStoresOnlyRefreshHashWithFamilyAndReturnsRawToken() {
        User user = activeUser();
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        given(authentication.getPrincipal()).willReturn(new UserPrincipal(user));
        given(authenticationManager.authenticate(any(Authentication.class)))
                .willReturn(authentication);
        given(clock.instant()).willReturn(NOW);
        given(refreshTokenSecrets.generate()).willReturn(
                new RefreshTokenSecrets.GeneratedRefreshToken("raw-token", "token-hash")
        );
        given(jwtService.refreshTokenExpiration()).willReturn(Duration.ofDays(7));
        given(jwtService.accessTokenExpiration()).willReturn(Duration.ofMinutes(15));
        given(jwtService.issue(user.getId(), "USER")).willReturn(
                new JwtService.IssuedAccessToken(
                        "access-token",
                        NOW.plus(Duration.ofMinutes(15))
                )
        );

        TokenResponse response = authenticationService.login(
                new LoginRequest("minh.nguyen", "StrongPassword#2026", "phone"),
                new ClientMetadata(null, null)
        );

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken stored = captor.getValue();
        assertThat(stored.getTokenHash()).isEqualTo("token-hash");
        assertThat(stored.getTokenHash()).isNotEqualTo(response.refreshToken());
        assertThat(stored.getTokenFamilyId()).isNotNull();
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("raw-token");
    }

    @Test
    void refreshMapsReuseToStableUnauthorizedError() {
        given(refreshTokenRotationService.rotate(any(), any())).willReturn(
                RefreshTokenRotationService.RotationResult.failure(
                        RefreshTokenRotationService.RotationStatus.REUSED
                )
        );

        assertThatThrownBy(() -> authenticationService.refresh(
                "reused-token",
                new ClientMetadata(null, null)
        )).isInstanceOfSatisfying(UnauthorizedException.class, exception ->
                assertThat(exception.code()).isEqualTo("TOKEN_REUSE_DETECTED")
        );
    }

    @Test
    void passwordChangeUpdatesHashAndRevokesAllRefreshTokens() {
        User user = activeUser();
        given(userRepository.findByIdAndStatusAndDeletedAtIsNull(
                user.getId(),
                UserStatus.ACTIVE
        )).willReturn(Optional.of(user));
        given(passwordEncoder.matches("current-password", user.getPasswordHash()))
                .willReturn(true);
        given(passwordEncoder.matches("NewPassword#2026", user.getPasswordHash()))
                .willReturn(false);
        given(passwordEncoder.encode("NewPassword#2026")).willReturn("new-hash");
        given(clock.instant()).willReturn(NOW);

        authenticationService.changePassword(
                user.getId(),
                "current-password",
                "NewPassword#2026"
        );

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(refreshTokenRepository).revokeAllForUser(
                user.getId(),
                NOW,
                "PASSWORD_CHANGED"
        );
    }

    private User activeUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .username("minh.nguyen")
                .email("minh@example.com")
                .emailVerified(true)
                .displayName("Minh Nguyen")
                .passwordHash("old-hash")
                .build();
    }
}
