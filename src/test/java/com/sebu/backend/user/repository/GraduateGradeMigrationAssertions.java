package com.sebu.backend.user.repository;

import org.flywaydb.core.Flyway;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class GraduateGradeMigrationAssertions {
    private GraduateGradeMigrationAssertions() { }

    static void verify(String url, String username, String password, boolean upgrade) throws Exception {
        if (upgrade) {
            Flyway.configure().dataSource(url, username, password)
                .locations("classpath:db/migration").target("40").load().migrate();
            try (var connection = DriverManager.getConnection(url, username, password);
                 var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO app_user (email) VALUES ('unselected@example.com')");
                for (int grade = 1; grade <= 4; grade++) {
                    statement.executeUpdate("""
                        INSERT INTO app_user (email, grade, academic_field, introduction)
                        VALUES ('year-%d@example.com', %d, 'ENGINEERING', 'Existing introduction')
                        """.formatted(grade, grade));
                }
                assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE app_user SET grade = 5 WHERE email = 'year-4@example.com'"
                )).isInstanceOf(SQLException.class);
            }
        }

        var flyway = Flyway.configure().dataSource(url, username, password)
            .locations("classpath:db/migration").load();
        flyway.migrate();
        flyway.validate();

        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            if (upgrade) {
                try (var rows = statement.executeQuery(
                    "SELECT grade, academic_field, introduction FROM app_user WHERE email LIKE 'year-%' ORDER BY grade"
                )) {
                    for (int grade = 1; grade <= 4; grade++) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getInt("grade")).isEqualTo(grade);
                        assertThat(rows.getString("academic_field")).isEqualTo("ENGINEERING");
                        assertThat(rows.getString("introduction")).isEqualTo("Existing introduction");
                    }
                    assertThat(rows.next()).isFalse();
                }
                try (var row = statement.executeQuery(
                    "SELECT grade FROM app_user WHERE email = 'unselected@example.com'"
                )) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getObject("grade")).isNull();
                }
            }

            statement.executeUpdate("INSERT INTO app_user (email) VALUES ('graduate@example.com')");
            for (int grade = 1; grade <= 5; grade++) {
                assertThat(statement.executeUpdate(
                    "UPDATE app_user SET grade = %d WHERE email = 'graduate@example.com'".formatted(grade)
                )).isOne();
                try (var row = statement.executeQuery(
                    "SELECT grade FROM app_user WHERE email = 'graduate@example.com'"
                )) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getInt("grade")).isEqualTo(grade);
                }
            }
            for (int invalid : new int[]{0, 6}) {
                assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE app_user SET grade = %d WHERE email = 'graduate@example.com'".formatted(invalid)
                )).isInstanceOf(SQLException.class);
            }
            assertThat(statement.executeUpdate(
                "UPDATE app_user SET grade = NULL WHERE email = 'graduate@example.com'"
            )).isOne();
        }
    }
}
