package com.example.financemanager.auth.infrastructure.security;

import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock JwtService jwtService;
    @Mock CustomUserDetailsService userDetailsService;
    @Mock AuthenticationEntryPoint authenticationEntryPoint;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validAccessTokenCreatesSecurityContext() throws Exception {
        UUID userId = UUID.randomUUID();
        JwtService.AccessTokenClaims claims = new JwtService.AccessTokenClaims(
                userId,
                "USER",
                UUID.randomUUID().toString(),
                Instant.now().plusSeconds(900)
        );
        UserPrincipal principal = org.mockito.Mockito.mock(UserPrincipal.class);
        given(jwtService.parse("valid-token")).willReturn(claims);
        given(userDetailsService.loadActiveUserById(userId)).willReturn(principal);
        given(principal.getAuthorities()).willReturn(java.util.List.of());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isSameAs(principal);
        verify(authenticationEntryPoint, never()).commence(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void invalidTokenUsesAuthenticationEntryPointWithoutContinuing() throws Exception {
        given(jwtService.parse("invalid-token"))
                .willThrow(new MalformedJwtException("invalid"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(authenticationEntryPoint).commence(
                org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.eq(response),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void malformedBearerHeaderIsRejectedBeforeParsing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic credentials");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, new MockFilterChain());

        verify(jwtService, never()).parse(org.mockito.ArgumentMatchers.anyString());
        verify(authenticationEntryPoint).commence(
                org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.eq(response),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private JwtAuthenticationFilter filter() {
        return new JwtAuthenticationFilter(
                jwtService,
                userDetailsService,
                authenticationEntryPoint
        );
    }
}
