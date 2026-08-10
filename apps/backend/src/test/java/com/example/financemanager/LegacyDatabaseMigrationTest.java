package com.example.financemanager;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class LegacyDatabaseMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("finance_manager_local")
                    .withUsername("test_user")
                    .withPassword("test_password");

    @Test
    void baselinesDbSqlAtNineAndAppliesAllForwardMigrations() throws Exception {
        Path dbSql = Path.of(System.getProperty("user.dir"))
                .resolve("../..")
                .resolve("db.sql")
                .normalize();

        assertThat(dbSql).isRegularFile();

        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(dbSql));
            statement.execute("DROP TABLE finance.email_verification_tokens");
            statement.execute("ALTER TABLE finance.users DROP COLUMN email_verified");
            statement.execute("""
                    insert into finance.users
                        (username, email, password_hash, display_name)
                    values
                        ('legacy.user', 'legacy@example.com', 'legacy-hash', 'Legacy User')
                    """);
        }

        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas("finance")
                .defaultSchema("finance")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("9"))
                .baselineDescription("Existing db.sql schema")
                .load();

        flyway.migrate();

        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement()) {
            try (ResultSet history = statement.executeQuery(
                    """
                    select version, type
                      from finance.flyway_schema_history
                     order by installed_rank
                    """)) {
                assertThat(history.next()).isTrue();
                assertThat(history.getString("version")).isEqualTo("9");
                assertThat(history.getString("type")).isEqualTo("BASELINE");
                assertThat(history.next()).isTrue();
                assertThat(history.getString("version")).isEqualTo("10");
                assertThat(history.next()).isTrue();
                assertThat(history.getString("version")).isEqualTo("11");
                assertThat(history.next()).isTrue();
                assertThat(history.getString("version")).isEqualTo("12");
                assertThat(history.next()).isFalse();
            }

            try (ResultSet column = statement.executeQuery(
                    """
                    select is_nullable, column_default
                      from information_schema.columns
                     where table_schema = 'finance'
                       and table_name = 'users'
                       and column_name = 'email_verified'
                    """)) {
                assertThat(column.next()).isTrue();
                assertThat(column.getString("is_nullable")).isEqualTo("NO");
                assertThat(column.getString("column_default")).contains("false");
            }

            try (ResultSet tokenTable = statement.executeQuery(
                    "select to_regclass('finance.email_verification_tokens')")) {
                assertThat(tokenTable.next()).isTrue();
                assertThat(tokenTable.getString(1))
                        .isEqualTo("finance.email_verification_tokens");
            }

            try (ResultSet legacyUser = statement.executeQuery(
                    """
                    select email_verified
                      from finance.users
                     where username = 'legacy.user'
                    """)) {
                assertThat(legacyUser.next()).isTrue();
                assertThat(legacyUser.getBoolean("email_verified")).isTrue();
            }

            try (ResultSet constraint = statement.executeQuery(
                    """
                    select pg_get_constraintdef(oid) as definition
                      from pg_constraint
                     where conname = 'ck_accounts_type'
                    """)) {
                assertThat(constraint.next()).isTrue();
                assertThat(constraint.getString("definition"))
                        .contains("SAVINGS")
                        .doesNotContain("'SAVING'");
            }

            try (ResultSet functionConfig = statement.executeQuery(
                    """
                    select array_to_string(proconfig, ',') as configuration
                      from pg_proc
                     where proname = 'create_default_notification_settings'
                    """)) {
                assertThat(functionConfig.next()).isTrue();
                assertThat(functionConfig.getString("configuration"))
                        .contains("search_path=finance, public");
            }
        }
    }
}
