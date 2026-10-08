package com.sebu.backend.auth.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SejongDepartmentNameMigrationTest {
    @Test
    void emptyDatabaseMigratesThroughLatestVersion() {
        String url = databaseUrl("empty");

        Flyway flyway = flyway(url, null);

        flyway.migrate();

        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void v14DataSurvivesLatestVersionWithoutUnsafeBackfill() throws Exception {
        String url = databaseUrl("upgrade");
        flyway(url, "14").migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO app_user (provider, provider_user_id, profile_completed, name)
                VALUES ('SEJONG', 'legacy-student', FALSE, '기존사용자')
                """);
        }

        flyway(url, null).migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("""
                SELECT name, sejong_department_name, nickname, version
                FROM app_user WHERE provider_user_id = 'legacy-student'
                """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("name")).isEqualTo("기존사용자");
                assertThat(result.getString("sejong_department_name")).isNull();
                assertThat(result.getString("nickname")).isNull();
                assertThat(result.getLong("version")).isZero();
            }

            statement.executeUpdate("""
                UPDATE app_user
                SET sejong_department_name = '무인이동체공학전공'
                WHERE provider_user_id = 'legacy-student'
                """);
            assertThatThrownBy(() -> statement.executeUpdate("""
                UPDATE app_user
                SET sejong_department_name = '12345678901234567890123456789012345678901234567890'
                    || '123456789012345678901234567890123456789012345678901'
                WHERE provider_user_id = 'legacy-student'
                """)).isInstanceOf(SQLException.class);
        }
    }

    private String databaseUrl(String suffix) {
        return "jdbc:h2:mem:sejong-department-name-" + suffix + "-" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }

    private Flyway flyway(String url, String target) {
        var configuration = Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }
}
