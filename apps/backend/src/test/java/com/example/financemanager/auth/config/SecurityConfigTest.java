package com.example.financemanager.auth.config;

import com.example.financemanager.auth.infrastructure.security.CustomUserDetailsService;
import com.example.financemanager.shared.config.CorsProperties;
import com.example.financemanager.shared.logging.CorrelationIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SecurityConfig securityConfig = new SecurityConfig(
            mock(CustomUserDetailsService.class),
            objectMapper
    );

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void passwordEncoderUsesBcryptCostTwelve() {
        PasswordEncoder passwordEncoder = securityConfig.passwordEncoder();

        String encodedPassword = passwordEncoder.encode("StrongPassword#2026");

        assertThat(encodedPassword).startsWith("$2a$12$");
        assertThat(passwordEncoder.matches("StrongPassword#2026", encodedPassword)).isTrue();
    }

    @Test
    void corsConfigurationUsesRestrictiveAllowList() {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
        CorsConfiguration configuration = securityConfig
                .corsConfigurationSource(new CorsProperties(List.of("https://app.example.com")))
                .getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly("https://app.example.com");
        assertThat(configuration.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(configuration.getAllowedHeaders())
                .containsExactly("Authorization", "Content-Type", "X-Correlation-ID");
        assertThat(configuration.getAllowCredentials()).isFalse();
    }

    @Test
    void authenticationEntryPointWritesStandardErrorEnvelope() throws Exception {
        MDC.put(CorrelationIdFilter.MDC_KEY, "security-test");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityConfig.authenticationEntryPoint().commence(
                request,
                response,
                new InsufficientAuthenticationException("Missing token")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(body.path("path").asText()).isEqualTo("/api/v1/accounts");
        assertThat(body.path("correlationId").asText()).isEqualTo("security-test");
    }

    @Test
    void accessDeniedHandlerWritesStandardErrorEnvelope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityConfig.accessDeniedHandler().handle(
                request,
                response,
                new AccessDeniedException("Forbidden")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(body.path("path").asText()).isEqualTo("/internal");
    }
}
