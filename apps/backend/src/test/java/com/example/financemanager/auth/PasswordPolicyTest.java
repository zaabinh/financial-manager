package com.example.financemanager.auth;

import com.example.financemanager.auth.domain.PasswordPolicy;
import com.example.financemanager.shared.error.UnprocessableEntityException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    @Test
    void acceptsPasswordMeetingEveryRule() {
        assertThatCode(() -> passwordPolicy.validate(
                "StrongPassword#2026",
                "minh.nguyen"
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingComplexityAndShortPasswords() {
        assertThatThrownBy(() -> passwordPolicy.validate("weakpassword", "minh"))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessage("Password does not meet the security policy");
    }

    @Test
    void rejectsPasswordContainingUsername() {
        assertThatThrownBy(() -> passwordPolicy.validate(
                "Minh.Nguyen#2026A",
                "minh.nguyen"
        )).isInstanceOf(UnprocessableEntityException.class);
    }

    @Test
    void rejectsPasswordOverBcryptUtf8Limit() {
        String password = "Aa1#" + "á".repeat(35);

        assertThatThrownBy(() -> passwordPolicy.validate(password, "minh"))
                .isInstanceOf(UnprocessableEntityException.class);
    }
}
