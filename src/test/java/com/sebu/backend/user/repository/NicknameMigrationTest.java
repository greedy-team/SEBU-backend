package com.sebu.backend.user.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NicknameMigrationTest {
    private static final String USERNAME = "sa";
    private static final String PASSWORD = "";

    @Test
    void v25_닉네임을_보정하고_고유_제약을_추가한다() throws SQLException {
        String url = databaseUrl("nickname-upgrade");
        flyway(url, "25").migrate();

        try (Connection connection = DriverManager.getConnection(url, USERNAME, PASSWORD)) {
            execute(connection,
                    "INSERT INTO app_user (email, nickname) VALUES (?, ?)",
                    "legacy@example.com", " ＬｅｇａｃｙＮａｍｅ ");
            execute(connection,
                    "INSERT INTO app_user (email, nickname) VALUES (?, ?)",
                    "empty@example.com", null);
        }

        flyway(url, null).migrate();

        try (Connection connection = DriverManager.getConnection(url, USERNAME, PASSWORD)) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT nickname, nickname_normalized FROM app_user WHERE email = ?")) {
                statement.setString(1, "legacy@example.com");
                try (ResultSet result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("nickname")).isEqualTo("LegacyName");
                    assertThat(result.getString("nickname_normalized")).isEqualTo("legacyname");
                }
            }

            assertThatThrownBy(() -> execute(connection,
                    "INSERT INTO app_user (email, nickname, nickname_normalized) VALUES (?, ?, ?)",
                    "duplicate@example.com", "LEGACYNAME", "legacyname"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection,
                    "UPDATE app_user SET nickname = ?, nickname_normalized = NULL WHERE email = ?",
                    "broken", "empty@example.com"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void 기존_정규화_닉네임이_충돌하면_마이그레이션이_실패한다() throws SQLException {
        String url = databaseUrl("nickname-collision");
        flyway(url, "25").migrate();
        try (Connection connection = DriverManager.getConnection(url, USERNAME, PASSWORD)) {
            execute(connection, "INSERT INTO app_user (email, nickname) VALUES (?, ?)",
                    "first@example.com", "ＳａｍｅＮａｍｅ");
            execute(connection, "INSERT INTO app_user (email, nickname) VALUES (?, ?)",
                    "second@example.com", " samename ");
        }

        assertThatThrownBy(() -> flyway(url, null).migrate())
                .isInstanceOf(RuntimeException.class);
    }

    private String databaseUrl(String prefix) {
        return "jdbc:h2:mem:" + prefix + "-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
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

    private void execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeUpdate();
        }
    }
}
