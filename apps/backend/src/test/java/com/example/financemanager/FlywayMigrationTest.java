package com.example.financemanager;

import com.example.financemanager.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void appliesAllMigrations() {
        Integer version = jdbcTemplate.queryForObject(
                "select max(version::int) from finance.flyway_schema_history where success",
                Integer.class
        );

        assertThat(version).isEqualTo(12);
    }

    @Test
    void enforcesCorrectedSavingsAccountType() {
        String definition = jdbcTemplate.queryForObject(
                """
                select pg_get_constraintdef(oid)
                  from pg_constraint
                 where conname = 'ck_accounts_type'
                """,
                String.class
        );

        assertThat(definition)
                .contains("SAVINGS")
                .doesNotContain("'SAVING'");
    }

    @Test
    void createsNotificationSettingsWithNewUser() {
        UUID userId = jdbcTemplate.queryForObject(
                """
                insert into finance.users (username, password_hash, display_name)
                values (?, ?, ?)
                returning id
                """,
                UUID.class,
                "phase2-user",
                "$2a$12$phase2.test.hash.value",
                "Phase Two User"
        );

        Integer settingsCount = jdbcTemplate.queryForObject(
                "select count(*) from finance.notification_settings where user_id = ?",
                Integer.class,
                userId
        );

        assertThat(settingsCount).isOne();
    }

    @Test
    void initializesBalanceCacheFromInitialBalance() {
        UUID userId = jdbcTemplate.queryForObject(
                """
                insert into finance.users (username, password_hash, display_name)
                values (?, ?, ?)
                returning id
                """,
                UUID.class,
                "balance-user",
                "$2a$12$phase2.test.hash.value",
                "Balance User"
        );

        java.math.BigDecimal balance = jdbcTemplate.queryForObject(
                """
                insert into finance.accounts (user_id, name, type, initial_balance)
                values (?, ?, 'SAVINGS', 125.5000)
                returning current_balance
                """,
                java.math.BigDecimal.class,
                userId,
                "Savings"
        );

        assertThat(balance).isEqualByComparingTo("125.5000");
    }
}
