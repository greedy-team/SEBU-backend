package com.sebu.backend.user.repository;

import com.sebu.backend.user.domain.AcademicField;
import org.flywaydb.core.Flyway;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class AcademicFieldMigrationAssertions {
    private AcademicFieldMigrationAssertions() { }

    static void verify(String url, String username, String password, boolean upgrade) throws Exception {
        if (upgrade) {
            Flyway.configure().dataSource(url, username, password)
                .locations("classpath:db/migration").target("39").load().migrate();
            try (var connection = DriverManager.getConnection(url, username, password);
                 var statement = connection.createStatement()) {
                statement.executeUpdate("""
                    INSERT INTO app_user (email, name, nickname, nickname_normalized, sejong_department_name, grade,
                                          profile_completed, introduction, profile_updated_at)
                    VALUES ('academic-legacy@example.com', 'Legacy Student', 'Legacy Nickname', 'legacy nickname',
                            'Legacy Department', 3, TRUE, 'Existing introduction', '2026-09-10 11:00:00')
                    """);
            }
        }

        var flyway = Flyway.configure().dataSource(url, username, password)
            .locations("classpath:db/migration").load();
        flyway.migrate();
        flyway.validate();

        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            if (!upgrade) {
                statement.executeUpdate("INSERT INTO app_user (email) VALUES ('academic-legacy@example.com')");
            }
            try (var result = statement.executeQuery("""
                SELECT name, nickname, sejong_department_name, grade, profile_completed,
                       introduction, profile_updated_at, academic_field
                FROM app_user WHERE email = 'academic-legacy@example.com'
                """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("academic_field")).isNull();
                if (upgrade) {
                    assertThat(result.getString("name")).isEqualTo("Legacy Student");
                    assertThat(result.getString("nickname")).isEqualTo("Legacy Nickname");
                    assertThat(result.getString("sejong_department_name")).isEqualTo("Legacy Department");
                    assertThat(result.getInt("grade")).isEqualTo(3);
                    assertThat(result.getBoolean("profile_completed")).isTrue();
                    assertThat(result.getString("introduction")).isEqualTo("Existing introduction");
                    assertThat(result.getTimestamp("profile_updated_at").toLocalDateTime())
                        .isEqualTo("2026-09-10T11:00:00");
                }
            }
            try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "app_user", "academic_field")) {
                assertThat(columns.next()).isTrue();
                assertThat(columns.getInt("COLUMN_SIZE")).isEqualTo(32);
                assertThat(columns.getString("IS_NULLABLE")).isEqualTo("YES");
            }
            for (AcademicField field : AcademicField.values()) {
                try (var update = connection.prepareStatement("UPDATE app_user SET academic_field = ? WHERE email = ?")) {
                    update.setString(1, field.name());
                    update.setString(2, "academic-legacy@example.com");
                    assertThat(update.executeUpdate()).isOne();
                }
                try (var result = statement.executeQuery("SELECT academic_field FROM app_user WHERE email = 'academic-legacy@example.com'")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo(field.name());
                }
            }
            assertThatThrownBy(() -> statement.executeUpdate("""
                UPDATE app_user SET academic_field = 'MEDICAL_HEALTH' WHERE email = 'academic-legacy@example.com'
                """)).isInstanceOf(SQLException.class);
            assertThat(statement.executeUpdate("""
                UPDATE app_user SET academic_field = NULL WHERE email = 'academic-legacy@example.com'
                """)).isOne();
        }
    }
}
