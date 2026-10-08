package com.sebu.backend.auth.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@Tag("mysql")
class AuthenticationMySqlMigrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
        .withCommand("--innodb-buffer-pool-size=64M")
        .withStartupTimeout(Duration.ofMinutes(4));

    @Test
    void upgradesExistingV12DataThroughLatestAuthenticationSchemaOnMySql8() throws Exception {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("12"))
            .load()
            .migrate();

        try (var connection = DriverManager.getConnection(
            MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()
        ); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO app_user (email) VALUES ('legacy@example.com')");
        }

        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("36"))
            .load().migrate();

        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO refresh_token (user_id, token_hash, expires_at, created_at)
                SELECT id, REPEAT('a', 64), '2026-10-01 00:00:00', '2026-09-17 00:00:00'
                FROM app_user WHERE email = 'legacy@example.com'
                """);
        }

        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();

        try (var connection = DriverManager.getConnection(
            MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()
        ); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("""
                SELECT email, provider, provider_user_id, profile_completed, sejong_department_name,
                       nickname, version, auth_version, anonymized_at
                FROM app_user
                WHERE email = 'legacy@example.com'
                """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("email")).isEqualTo("legacy@example.com");
                assertThat(result.getString("provider")).isNull();
                assertThat(result.getString("provider_user_id")).isNull();
                assertThat(result.getBoolean("profile_completed")).isFalse();
                assertThat(result.getString("sejong_department_name")).isNull();
                assertThat(result.getString("nickname")).isNull();
                assertThat(result.getLong("version")).isZero();
                assertThat(result.getLong("auth_version")).isZero();
                assertThat(result.getTimestamp("anonymized_at")).isNull();
            }

            statement.executeUpdate("""
                INSERT INTO app_user (provider, provider_user_id, profile_completed)
                VALUES ('SEJONG', '21012345', FALSE)
                """);

            assertThatThrownBy(() -> statement.executeUpdate("""
                INSERT INTO app_user (provider, provider_user_id, profile_completed)
                VALUES ('SEJONG', '21012345', FALSE)
                """))
                .isInstanceOf(SQLException.class);

            statement.executeUpdate("""
                INSERT INTO account_recovery_token (user_id, token_hash, expires_at, created_at)
                SELECT id, REPEAT('b', 64), '2026-10-01 00:05:00', '2026-10-01 00:00:00'
                FROM app_user WHERE provider_user_id = '21012345'
                """);
            assertThatThrownBy(() -> statement.executeUpdate("""
                INSERT INTO account_recovery_token (user_id, token_hash, expires_at, created_at)
                SELECT id, REPEAT('c', 64), '2026-10-01 00:05:00', '2026-10-01 00:00:00'
                FROM app_user WHERE provider_user_id = '21012345'
                """))
                .isInstanceOf(SQLException.class);
            try (var result = statement.executeQuery("""
                SELECT COUNT(*) AS token_count FROM account_recovery_token
                WHERE token_hash = REPEAT('b', 64)
                """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong("token_count")).isOne();
            }

            try (var result = statement.executeQuery("""
                SELECT session_id, revoked_at, expires_at, absolute_expires_at
                FROM refresh_token WHERE token_hash = REPEAT('a', 64)
                """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("session_id")).startsWith("legacy-");
                assertThat(result.getTimestamp("revoked_at")).isNotNull();
                assertThat(result.getTimestamp("absolute_expires_at")).isEqualTo(result.getTimestamp("expires_at"));
            }
            assertThatThrownBy(() -> statement.executeUpdate("""
                UPDATE refresh_token SET absolute_expires_at = '2026-09-18 00:00:00'
                WHERE token_hash = REPEAT('a', 64)
                """))
                .isInstanceOf(SQLException.class);
        }
    }
}
