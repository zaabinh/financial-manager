package com.example.financemanager.auth;

import com.example.financemanager.auth.domain.RefreshTokenSecrets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenSecretsTest {

    private final RefreshTokenSecrets refreshTokenSecrets = new RefreshTokenSecrets();

    @Test
    void generatesOpaqueUrlSafeTokensAndStableHashes() {
        RefreshTokenSecrets.GeneratedRefreshToken first = refreshTokenSecrets.generate();
        RefreshTokenSecrets.GeneratedRefreshToken second = refreshTokenSecrets.generate();

        assertThat(first.rawValue())
                .hasSize(43)
                .matches("^[A-Za-z0-9_-]+$")
                .doesNotContain(".");
        assertThat(first.hash()).hasSize(64).matches("^[0-9a-f]+$");
        assertThat(refreshTokenSecrets.hash(first.rawValue())).isEqualTo(first.hash());
        assertThat(second.rawValue()).isNotEqualTo(first.rawValue());
        assertThat(second.hash()).isNotEqualTo(first.hash());
    }
}
