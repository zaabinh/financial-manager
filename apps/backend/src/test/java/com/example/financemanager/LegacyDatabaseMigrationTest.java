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
                    .withDatabaseName("finance_manager_legacy_test")
                    .withUsername("test_user")
                    .withPassword("test_password");

    @Test
    void baselinesDbSqlAtNineAndAppliesReconciliation() throws Exception {
        Path dbSql = Path.of(System.getProperty("user.dir"))
                .resolve("../..")
                .resolve("db.sql")
                .normalize();

        assertThat(dbSql).isRegularFile();

        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(dbSql));
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
                assertThat(history.next()).isFalse();
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
