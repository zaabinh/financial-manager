package com.example.financemanager.auth.config;

import com.example.financemanager.auth.infrastructure.security.CustomUserDetailsService;
import com.example.financemanager.auth.infrastructure.security.JwtService;
import com.example.financemanager.shared.config.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityFilterChainTest.TestController.class)
@Import({
        SecurityConfig.class,
        SecurityFilterChainTest.TestController.class,
        SecurityFilterChainTest.TestBeans.class
})
class SecurityFilterChainTest {

    private final MockMvc mockMvc;

    @Autowired
    SecurityFilterChainTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void publicAuthEndpointDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login"))
                .andExpect(status().isNoContent());
    }

    @Test
    void publicEmailVerificationEndpointDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm"))
                .andExpect(status().isNoContent());
    }

    @Test
    void protectedApiEndpointReturnsStandardUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @WithMockUser
    void authenticatedUserCanAccessApiEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/test"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void nonApiEndpointReturnsStandardForbiddenResponse() throws Exception {
        mockMvc.perform(get("/internal"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void configuredOriginPassesPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:3000"
                ));
    }

    @RestController
    public static class TestController {

        @PostMapping("/api/v1/auth/login")
        ResponseEntity<Void> login() {
            return ResponseEntity.noContent().build();
        }

        @PostMapping("/api/v1/auth/email-verification/confirm")
        ResponseEntity<Void> confirmEmailVerification() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/api/v1/test")
        ResponseEntity<Void> protectedEndpoint() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/internal")
        ResponseEntity<Void> internalEndpoint() {
            return ResponseEntity.noContent().build();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        CustomUserDetailsService customUserDetailsService() {
            return mock(CustomUserDetailsService.class);
        }

        @Bean
        JwtService jwtService() {
            return mock(JwtService.class);
        }

        @Bean
        CorsProperties corsProperties() {
            return new CorsProperties(List.of("http://localhost:3000"));
        }
    }
}
