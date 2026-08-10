package com.example.financemanager.auth;

import com.example.financemanager.auth.domain.RefreshTokenSecrets;
import com.example.financemanager.auth.infrastructure.persistence.entity.RefreshToken;
import com.example.financemanager.auth.infrastructure.persistence.repository.EmailVerificationTokenRepository;
import com.example.financemanager.auth.infrastructure.persistence.repository.RefreshTokenRepository;
import com.example.financemanager.notification.application.MailService;
import com.example.financemanager.notification.domain.MailMessage;
import com.example.financemanager.support.PostgresIntegrationTest;
import com.example.financemanager.user.infrastructure.persistence.entity.User;
import com.example.financemanager.user.infrastructure.persistence.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthenticationFlowIntegrationTest extends PostgresIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Autowired RefreshTokenSecrets refreshTokenSecrets;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean MailService mailService;

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAll();
        emailVerificationTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void supportsCompleteAuthenticationLifecycle() throws Exception {
        String verificationToken = register();

        User user = userRepository.findByUsernameIgnoreCase("MINH.NGUYEN").orElseThrow();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(passwordEncoder.matches("StrongPassword#2026", user.getPasswordHash()))
                .isTrue();
        Integer settingCount = jdbcTemplate.queryForObject(
                "select count(*) from finance.notification_settings where user_id = ?",
                Integer.class,
                user.getId()
        );
        assertThat(settingCount).isEqualTo(1);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("StrongPassword#2026", "before-verification")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));

        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "token", verificationToken
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailVerified").value(true));
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified())
                .isTrue();

        Tokens first = login("StrongPassword#2026", "phone-a");
        assertThat(first.accessToken().split("\\.")).hasSize(3);
        assertThat(first.refreshToken()).doesNotContain(".");
        assertThat(refreshTokenRepository.findAll())
                .extracting(RefreshToken::getTokenHash)
                .contains(refreshTokenSecrets.hash(first.refreshToken()))
                .doesNotContain(first.refreshToken());

        Tokens rotated = refresh(first.refreshToken());
        assertThat(rotated.refreshToken()).isNotEqualTo(first.refreshToken());
        RefreshToken original = refreshTokenRepository.findAll().stream()
                .filter(token -> token.getTokenHash().equals(
                        refreshTokenSecrets.hash(first.refreshToken())
                ))
                .findFirst()
                .orElseThrow();
        assertThat(original.getRevokeReason()).isEqualTo("ROTATED");
        assertThat(original.getReplacedByTokenId()).isNotNull();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(first.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_REUSE_DETECTED"));
        List<RefreshToken> reusedFamily = refreshTokenRepository.findAll().stream()
                .filter(token -> token.getTokenFamilyId().equals(original.getTokenFamilyId()))
                .toList();
        assertThat(reusedFamily).allMatch(RefreshToken::isRevoked);

        Tokens deviceA = login("StrongPassword#2026", "phone-a");
        Tokens deviceB = login("StrongPassword#2026", "phone-b");
        logout(deviceA.accessToken(), deviceA.refreshToken());
        logout(deviceA.accessToken(), deviceA.refreshToken());
        assertRefreshRejected(deviceA.refreshToken(), "TOKEN_REVOKED");

        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(deviceB.accessToken())))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEmpty());
        assertRefreshRejected(deviceB.refreshToken(), "TOKEN_REVOKED");

        Tokens passwordSession = login("StrongPassword#2026", "phone-c");
        mockMvc.perform(put("/api/v1/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(passwordSession.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "StrongPassword#2026",
                                  "newPassword": "NewStrongPassword#2026"
                                }
                                """))
                .andExpect(status().isNoContent());
        assertRefreshRejected(passwordSession.refreshToken(), "TOKEN_REVOKED");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("StrongPassword#2026", "old-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        assertThat(login("NewStrongPassword#2026", "new-password").accessToken())
                .isNotBlank();
    }

    @Test
    void concurrentRefreshAllowsOnlyOneRotation() throws Exception {
        registerAndVerify();
        Tokens tokens = login("StrongPassword#2026", "concurrent-device");
        String body = refreshBody(tokens.refreshToken());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Integer> refreshCall = () -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(refreshCall);
            Future<Integer> second = executor.submit(refreshCall);
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(200, 401);
        }
    }

    private String register() throws Exception {
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
                .andExpect(jsonPath("$.data.username").value("minh.nguyen"))
                .andExpect(jsonPath("$.data.emailVerified").value(false));

        org.mockito.ArgumentCaptor<MailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(MailMessage.class);
        verify(mailService).send(captor.capture());
        String verificationUrl = captor.getValue()
                .templateVariables()
                .get("verificationUrl")
                .toString();
        return UriComponentsBuilder.fromUriString(verificationUrl)
                .build()
                .getQueryParams()
                .getFirst("verificationToken");
    }

    private void registerAndVerify() throws Exception {
        String token = register();
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "token", token
                        ))))
                .andExpect(status().isOk());
    }

    private Tokens login(String password, String deviceName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(password, deviceName)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper
                .readTree(result.getResponse().getContentAsByteArray())
                .path("data");
        return new Tokens(
                data.path("accessToken").asText(),
                data.path("refreshToken").asText()
        );
    }

    private Tokens refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper
                .readTree(result.getResponse().getContentAsByteArray())
                .path("data");
        return new Tokens(
                data.path("accessToken").asText(),
                data.path("refreshToken").asText()
        );
    }

    private void logout(String accessToken, String refreshToken) throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEmpty());
    }

    private void assertRefreshRejected(String refreshToken, String code) throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(code));
    }

    private String loginBody(String password, String deviceName) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "username", "minh.nguyen",
                "password", password,
                "deviceName", deviceName
        ));
    }

    private String refreshBody(String refreshToken) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "refreshToken", refreshToken
        ));
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record Tokens(String accessToken, String refreshToken) {
    }
}
