package com.example.financemanager.auth.api;

import com.example.financemanager.auth.api.dto.response.TokenResponse;
import com.example.financemanager.auth.api.dto.response.UserResponse;
import com.example.financemanager.auth.application.AuthenticationService;
import com.example.financemanager.auth.application.EmailVerificationService;
import com.example.financemanager.user.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuthenticationService authenticationService;
    @MockitoBean EmailVerificationService emailVerificationService;

    @Test
    void registerUsesSharedEnvelopeAndLocationHeader() throws Exception {
        UserResponse user = userResponse();
        given(authenticationService.register(any())).willReturn(user);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "minh.nguyen",
                                  "email": "minh@example.com",
                                  "displayName": "Minh Nguyen",
                                  "password": "StrongPassword#2026",
                                  "currency": "VND",
                                  "timezone": "Asia/Ho_Chi_Minh"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/users/me"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("minh.nguyen"))
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void loginReturnsFlatDocumentedTokenResponse() throws Exception {
        TokenResponse token = new TokenResponse(
                "access-token",
                "opaque-refresh-token",
                900,
                604800,
                userResponse()
        );
        given(authenticationService.login(any(), any())).willReturn(token);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "minh.nguyen",
                                  "password": "StrongPassword#2026",
                                  "deviceName": "test-device"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("opaque-refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(900))
                .andExpect(jsonPath("$.data.refreshTokenExpiresIn").value(604800))
                .andExpect(jsonPath("$.data.user.username").value("minh.nguyen"));
    }

    @Test
    void invalidRegistrationUsesValidationErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void requestEmailVerificationDoesNotExposeAccountExistence() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"minh@example.com"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void confirmEmailVerificationUsesSharedEnvelope() throws Exception {
        given(emailVerificationService.confirm("verification-token"))
                .willReturn(userResponse());

        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"verification-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.emailVerified").value(true))
                .andExpect(jsonPath("$.message").value("Email verified successfully"));
    }

    private UserResponse userResponse() {
        return new UserResponse(
                UUID.randomUUID(),
                "minh@example.com",
                true,
                "minh.nguyen",
                "Minh Nguyen",
                UserRole.USER
        );
    }
}
