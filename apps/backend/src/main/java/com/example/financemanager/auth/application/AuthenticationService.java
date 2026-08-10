package com.example.financemanager.auth.application;

import com.example.financemanager.auth.api.dto.request.LoginRequest;
import com.example.financemanager.auth.api.dto.request.RegisterRequest;
import com.example.financemanager.auth.api.dto.response.TokenResponse;
import com.example.financemanager.auth.api.dto.response.UserResponse;
import com.example.financemanager.auth.domain.PasswordPolicy;
import com.example.financemanager.auth.domain.RefreshTokenSecrets;
import com.example.financemanager.auth.exception.UserAlreadyExistsException;
import com.example.financemanager.auth.infrastructure.persistence.entity.RefreshToken;
import com.example.financemanager.auth.infrastructure.persistence.repository.RefreshTokenRepository;
import com.example.financemanager.auth.infrastructure.security.JwtService;
import com.example.financemanager.auth.infrastructure.security.UserPrincipal;
import com.example.financemanager.shared.error.UnauthorizedException;
import com.example.financemanager.shared.error.ForbiddenException;
import com.example.financemanager.shared.error.UnprocessableEntityException;
import com.example.financemanager.user.domain.UserStatus;
import com.example.financemanager.user.infrastructure.persistence.entity.User;
import com.example.financemanager.user.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationService.class);
    private static final String DEFAULT_CURRENCY = "VND";
    private static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtService jwtService;
    private final RefreshTokenSecrets refreshTokenSecrets;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenRotationService refreshTokenRotationService;
    private final AuthenticationRateLimiter authenticationRateLimiter;
    private final EmailVerificationService emailVerificationService;
    private final Clock clock;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        // Validate and normalize request data
        String username = request.username().trim();
        String email = normalizeOptional(request.email());
        String currency = validateCurrency(request.currency());
        String timezone = validateTimezone(request.timezone());

        // Validate password
        passwordPolicy.validate(request.password(), username);

        if (userRepository.existsByUsernameIgnoreCase(username)
                || email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw duplicateUser();
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .displayName(request.displayName().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .currency(currency)
                .timezone(timezone)
                .build();

        try {
            User savedUser = userRepository.saveAndFlush(user);
            // Send verification email
            emailVerificationService.issueAndSend(savedUser);
            LOGGER.info("authentication_event=user_registered userId={}", savedUser.getId());
            
            return UserResponse.from(savedUser);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateUser();
        }
    }

    @Transactional
    public TokenResponse login(LoginRequest request, ClientMetadata clientMetadata) {
        authenticationRateLimiter.checkLoginAllowed(request.username(), clientMetadata);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.username().trim(),
                            request.password()
                    )
            );
        } catch (AuthenticationException exception) {
            authenticationRateLimiter.recordLoginFailure(request.username(), clientMetadata);
            LOGGER.warn("authentication_event=login_failed");
            throw invalidCredentials();
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = principal.getUser();
        if (user.getEmail() != null && !user.isEmailVerified()) {
            throw new ForbiddenException(
                    "EMAIL_NOT_VERIFIED",
                    "Email verification is required before login"
            );
        }
        authenticationRateLimiter.clearLoginFailures(request.username(), clientMetadata);
        Instant now = clock.instant();
        RefreshTokenSecrets.GeneratedRefreshToken generated =
                refreshTokenSecrets.generate();

        RefreshToken storedToken = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(generated.hash())
                .tokenFamilyId(UUID.randomUUID())
                .expiresAt(now.plus(jwtService.refreshTokenExpiration()))
                .userAgent(clientMetadata.userAgent())
                .ipAddress(clientMetadata.ipAddress())
                .build();
        refreshTokenRepository.save(storedToken);

        LOGGER.info("authentication_event=login_succeeded userId={}", user.getId());
        return tokenResponse(user, generated.rawValue());
    }

    public TokenResponse refresh(String rawRefreshToken, ClientMetadata clientMetadata) {
        authenticationRateLimiter.checkAndRecordRefresh(clientMetadata);
        RefreshTokenRotationService.RotationResult result =
                refreshTokenRotationService.rotate(rawRefreshToken, clientMetadata);

        return switch (result.status()) {
            case SUCCESS -> {
                LOGGER.info(
                        "authentication_event=refresh_succeeded userId={}",
                        result.user().getId()
                );
                yield tokenResponse(result.user(), result.rawRefreshToken());
            }
            case EXPIRED -> throw new UnauthorizedException(
                    "TOKEN_EXPIRED",
                    "Refresh token has expired"
            );
            case REVOKED -> throw new UnauthorizedException(
                    "TOKEN_REVOKED",
                    "Refresh token has been revoked"
            );
            case REUSED -> throw new UnauthorizedException(
                    "TOKEN_REUSE_DETECTED",
                    "Refresh token reuse was detected"
            );
            case UNKNOWN, INACTIVE_USER -> throw new UnauthorizedException(
                    "INVALID_REFRESH_TOKEN",
                    "Refresh token is invalid"
            );
        };
    }

    @Transactional
    public void logout(UUID userId, String rawRefreshToken) {
        String tokenHash = refreshTokenSecrets.hash(rawRefreshToken);
        Instant now = clock.instant();

        refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .filter(token -> token.getUserId().equals(userId))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.revoke(now, "LOGOUT"));
        LOGGER.info("authentication_event=logout userId={}", userId);
    }

    @Transactional
    public void logoutAll(UUID userId) {
        refreshTokenRepository.revokeAllForUser(
                userId,
                clock.instant(),
                "LOGOUT_ALL"
        );
        LOGGER.info("authentication_event=logout_all userId={}", userId);
    }

    @Transactional
    public void changePassword(
            UUID userId,
            String currentPassword,
            String newPassword
    ) {
        User user = activeUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw invalidCredentials();
        }

        passwordPolicy.validate(newPassword, user.getUsername());
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new UnprocessableEntityException(
                    "PASSWORD_UNCHANGED",
                    "New password must differ from the current password"
            );
        }

        user.changePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(
                userId,
                clock.instant(),
                "PASSWORD_CHANGED"
        );
        LOGGER.info("authentication_event=password_changed userId={}", userId);
    }

    private TokenResponse tokenResponse(User user, String rawRefreshToken) {
        JwtService.IssuedAccessToken accessToken =
                jwtService.issue(user.getId(), user.getRole().name());
        return new TokenResponse(
                accessToken.value(),
                rawRefreshToken,
                jwtService.accessTokenExpiration().toSeconds(),
                jwtService.refreshTokenExpiration().toSeconds(),
                UserResponse.from(user)
        );
    }

    private User activeUser(UUID userId) {
        return userRepository
                .findByIdAndStatusAndDeletedAtIsNull(userId, UserStatus.ACTIVE)
                .orElseThrow(AuthenticationService::invalidCredentials);
    }

    private String validateCurrency(String requestedCurrency) {
        String currency = StringUtils.hasText(requestedCurrency)
                ? requestedCurrency.trim().toUpperCase(Locale.ROOT)
                : DEFAULT_CURRENCY;
        try {
            Currency.getInstance(currency);
            return currency;
        } catch (IllegalArgumentException exception) {
            throw new UnprocessableEntityException(
                    "INVALID_CURRENCY",
                    "Currency must be a valid ISO-4217 code"
            );
        }
    }

    private String validateTimezone(String requestedTimezone) {
        String timezone = StringUtils.hasText(requestedTimezone)
                ? requestedTimezone.trim()
                : DEFAULT_TIMEZONE;
        try {
            ZoneId.of(timezone);
            return timezone;
        } catch (DateTimeException exception) {
            throw new UnprocessableEntityException(
                    "INVALID_TIMEZONE",
                    "Timezone must be a valid IANA identifier"
            );
        }
    }

    private static String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static UserAlreadyExistsException duplicateUser() {
        return new UserAlreadyExistsException("Username or email already exists");
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("INVALID_CREDENTIALS", "Invalid credentials");
    }
}
