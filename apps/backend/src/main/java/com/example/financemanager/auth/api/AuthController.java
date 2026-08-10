package com.example.financemanager.auth.api;

import com.example.financemanager.auth.api.dto.request.ChangePasswordRequest;
import com.example.financemanager.auth.api.dto.request.ConfirmEmailVerificationRequest;
import com.example.financemanager.auth.api.dto.request.LoginRequest;
import com.example.financemanager.auth.api.dto.request.LogoutRequest;
import com.example.financemanager.auth.api.dto.request.RefreshRequest;
import com.example.financemanager.auth.api.dto.request.RegisterRequest;
import com.example.financemanager.auth.api.dto.request.RequestEmailVerificationRequest;
import com.example.financemanager.auth.api.dto.response.TokenResponse;
import com.example.financemanager.auth.api.dto.response.UserResponse;
import com.example.financemanager.auth.application.AuthenticationService;
import com.example.financemanager.auth.application.ClientMetadata;
import com.example.financemanager.auth.application.EmailVerificationService;
import com.example.financemanager.auth.infrastructure.security.UserPrincipal;
import com.example.financemanager.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        UserResponse user = authenticationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/users/me"))
                .body(ApiResponse.success(user, "User registered successfully"));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        TokenResponse response = authenticationService.login(
                request,
                clientMetadata(servletRequest, request.deviceName())
        );
        return ApiResponse.success(response, "Login successful");
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest servletRequest
    ) {
        TokenResponse response = authenticationService.refresh(
                request.refreshToken(),
                clientMetadata(servletRequest, null)
        );
        return ApiResponse.success(response, "Token refreshed successfully");
    }

    @PostMapping("/email-verification/request")
    public ResponseEntity<Void> requestEmailVerification(
            @Valid @RequestBody RequestEmailVerificationRequest request,
            HttpServletRequest servletRequest
    ) {
        emailVerificationService.request(
                request.email(),
                clientMetadata(servletRequest, null)
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email-verification/confirm")
    public ApiResponse<UserResponse> confirmEmailVerification(
            @Valid @RequestBody ConfirmEmailVerificationRequest request
    ) {
        UserResponse user = emailVerificationService.confirm(request.token());
        return ApiResponse.success(user, "Email verified successfully");
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody LogoutRequest request
    ) {
        authenticationService.logout(principal.getId(), request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        authenticationService.logoutAll(principal.getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authenticationService.changePassword(
                principal.getId(),
                request.currentPassword(),
                request.newPassword()
        );
        return ResponseEntity.noContent().build();
    }

    private ClientMetadata clientMetadata(
            HttpServletRequest request,
            String deviceName
    ) {
        return ClientMetadata.of(
                deviceName,
                request.getHeader(HttpHeaders.USER_AGENT),
                request.getRemoteAddr()
        );
    }
}
