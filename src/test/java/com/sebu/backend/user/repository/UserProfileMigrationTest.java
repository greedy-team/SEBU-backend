package com.sebu.backend.user.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileMigrationTest {
    private static final String USERNAME = "sa";
    private static final String PASSWORD = "";

    @Test
    void v12UserAndBookmarkDataSurviveV13Upgrade() throws SQLException {
        String databaseName = "user-profile-upgrade-" + UUID.randomUUID();
        String url = "jdbc:h2:mem:" + databaseName
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

        flyway(url, "12").migrate();

        long userId;
        long laboratoryId;
        try (Connection connection = DriverManager.getConnection(url, USERNAME, PASSWORD)) {
            long collegeId = insertAndReturnId(connection, "INSERT INTO college (name) VALUES (?)", "기존대학");
            long departmentId = insertAndReturnId(
                connection,
                "INSERT INTO department (college_id, name) VALUES (?, ?)",
                collegeId,
                "기존학과"
            );
            long professorId = insertAndReturnId(
                connection,
                "INSERT INTO professor (department_id, name, email) VALUES (?, ?, ?)",
                departmentId,
                "기존교수",
                "legacy-professor@example.com"
            );
            laboratoryId = insertAndReturnId(
                connection,
                """
                    INSERT INTO laboratory (professor_id, department_id, name, recruitment_status)
                    VALUES (?, ?, ?, ?)
                    """,
                professorId,
                departmentId,
                "기존연구실",
                "RECRUITING"
            );
            userId = insertAndReturnId(
                connection,
                "INSERT INTO app_user (email) VALUES (?)",
                "legacy-user@example.com"
            );
            executeUpdate(
                connection,
                "INSERT INTO bookmark (user_id, laboratory_id) VALUES (?, ?)",
                userId,
                laboratoryId
            );
        }

        flyway(url, null).migrate();

        try (Connection connection = DriverManager.getConnection(url, USERNAME, PASSWORD)) {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT email, name, nickname, grade, major_department_id, gpa_band, introduction,
                       profile_updated_at, version
                FROM app_user
                WHERE id = ?
                """)) {
                statement.setLong(1, userId);
                try (ResultSet result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("email")).isEqualTo("legacy-user@example.com");
                    assertThat(result.getString("name")).isNull();
                    assertThat(result.getString("nickname")).isNull();
                    assertThat(result.getObject("grade")).isNull();
                    assertThat(result.getObject("major_department_id")).isNull();
                    assertThat(result.getString("gpa_band")).isNull();
                    assertThat(result.getString("introduction")).isEmpty();
                    assertThat(result.getObject("profile_updated_at")).isNull();
                    assertThat(result.getLong("version")).isZero();
                }
            }

            assertThat(count(connection, "SELECT COUNT(*) FROM bookmark WHERE user_id = ? AND laboratory_id = ?", userId, laboratoryId))
                .isEqualTo(1L);
            assertThat(indexNames(connection, "bookmark")).contains("idx_bookmark_user_created_at");

            assertThatThrownBy(() -> executeUpdate(
                connection,
                "UPDATE app_user SET grade = ? WHERE id = ?",
                6,
                userId
            )).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> executeUpdate(
                connection,
                "UPDATE app_user SET gpa_band = ? WHERE id = ?",
                "GTE_2_0",
                userId
            )).isInstanceOf(SQLException.class);
        }
    }

    private Flyway flyway(String url, String target) {
        var configuration = Flyway.configure()
            .dataSource(url, USERNAME, PASSWORD)
            .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private long insertAndReturnId(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setParameters(statement, parameters);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Generated key was not returned");
                }
                return keys.getLong(1);
            }
        }
    }

    private void executeUpdate(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setParameters(statement, parameters);
            statement.executeUpdate();
        }
    }

    private long count(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setParameters(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private Set<String> indexNames(Connection connection, String tableName) throws SQLException {
        Set<String> names = new HashSet<>();
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(null, null, tableName, false, false)) {
            while (indexes.next()) {
                String name = indexes.getString("INDEX_NAME");
                if (name != null) {
                    names.add(name.toLowerCase());
                }
            }
        }
        return names;
    }

    private void setParameters(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }
}
