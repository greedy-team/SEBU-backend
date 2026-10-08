package com.sebu.backend.community.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class CommunityMySqlMigrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @BeforeEach
    void cleanDatabase() {
        flyway().clean();
    }

    @Test
    void v26ThroughV28ApplyWithExpectedConstraintsAndIndexes() throws Exception {
        flyway().migrate();

        try (Connection connection = connection()) {
            assertMigrationsApplied(connection);
            assertNicknameSchema(connection);
            assertCommunitySchema(connection);

            long authorId = insertUser(connection, "community-mysql-author@example.com", "작성자", "작성자");
            long readerId = insertUser(connection, "community-mysql-reader@example.com", "독자", "독자");
            assertDuplicateNicknameRejected(connection);
            insertUser(connection, "community-mysql-accent@example.com", "é", "é");
            insertUser(connection, "community-mysql-ascii@example.com", "e", "e");

            long freePostId = insertPost(connection, authorId, "FREE", "자유 글");
            long questionPostId = insertPost(connection, authorId, "QUESTION", "질문 글");
            assertThat(freePostId).isPositive();
            assertThat(questionPostId).isPositive();
            assertUnsupportedCategoryRejected(connection, authorId);
            assertCompositePrimaryKeysRejectDuplicates(connection, readerId, freePostId);
        }
    }

    private void assertMigrationsApplied(Connection connection) throws SQLException {
        assertThat(count(
                connection,
                """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE version IN ('26', '27', '28') AND success = 1
                        """
        )).isEqualTo(3);
    }

    private void assertNicknameSchema(Connection connection) throws SQLException {
        assertThat(count(
                connection,
                """
                        SELECT character_maximum_length
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'app_user'
                          AND column_name = 'nickname_normalized'
                        """
        )).isEqualTo(100);
        assertThat(text(
                connection,
                """
                        SELECT collation_name
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'app_user'
                          AND column_name = 'nickname_normalized'
                        """
        )).isEqualToIgnoringCase("utf8mb4_bin");
        assertThat(count(
                connection,
                """
                        SELECT COUNT(*)
                        FROM information_schema.table_constraints
                        WHERE table_schema = DATABASE()
                          AND table_name = 'app_user'
                          AND constraint_name IN (
                              'ck_app_user_nickname_pair',
                              'uk_app_user_nickname_normalized'
                          )
                        """
        )).isEqualTo(2);
    }

    private void assertCommunitySchema(Connection connection) throws SQLException {
        List<String> tables = List.of(
                "community_post",
                "community_comment",
                "community_post_like",
                "community_post_bookmark"
        );
        assertThat(count(
                connection,
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND table_name IN (?, ?, ?, ?)
                        """,
                tables.toArray()
        )).isEqualTo(tables.size());

        assertThat(count(
                connection,
                """
                        SELECT COUNT(*)
                        FROM information_schema.table_constraints
                        WHERE table_schema = DATABASE()
                          AND table_name IN (
                              'community_post',
                              'community_comment',
                              'community_post_like',
                              'community_post_bookmark'
                          )
                          AND constraint_type = 'PRIMARY KEY'
                        """
        )).isEqualTo(4);

        assertThat(count(
                connection,
                """
                        SELECT COUNT(*)
                        FROM information_schema.table_constraints
                        WHERE table_schema = DATABASE()
                          AND table_name = 'community_post'
                          AND constraint_type = 'CHECK'
                          AND constraint_name IN (
                              'ck_community_post_category',
                              'ck_community_post_view_count'
                          )
                        """
        )).isEqualTo(2);

        assertThat(count(
                connection,
                """
                        SELECT COUNT(DISTINCT index_name)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND index_name IN (
                              'idx_community_post_active_created',
                              'idx_community_post_category_active_created',
                              'idx_community_post_author_active_created',
                              'idx_community_comment_post_active_created',
                              'idx_community_comment_author_active',
                              'idx_community_post_like_post',
                              'idx_community_post_bookmark_post'
                          )
                        """
        )).isEqualTo(7);
    }

    private long insertUser(
            Connection connection,
            String email,
            String nickname,
            String nicknameNormalized
    ) throws SQLException {
        return insertAndReturnId(
                connection,
                "INSERT INTO app_user (email, nickname, nickname_normalized) VALUES (?, ?, ?)",
                email,
                nickname,
                nicknameNormalized
        );
    }

    private void assertDuplicateNicknameRejected(Connection connection) {
        assertThatThrownBy(() -> insertUser(
                connection,
                "community-mysql-duplicate@example.com",
                "중복 작성자",
                "작성자"
        )).isInstanceOf(SQLException.class);
    }

    private long insertPost(
            Connection connection,
            long authorId,
            String category,
            String title
    ) throws SQLException {
        return insertAndReturnId(
                connection,
                """
                        INSERT INTO community_post (author_id, category, title, content)
                        VALUES (?, ?, ?, ?)
                        """,
                authorId,
                category,
                title,
                "MySQL 마이그레이션 테스트 내용"
        );
    }

    private void assertUnsupportedCategoryRejected(Connection connection, long authorId) {
        assertThatThrownBy(() -> insertPost(connection, authorId, "NOTICE", "지원하지 않는 글"))
                .isInstanceOf(SQLException.class);
    }

    private void assertCompositePrimaryKeysRejectDuplicates(
            Connection connection,
            long userId,
            long postId
    ) throws SQLException {
        executeUpdate(
                connection,
                "INSERT INTO community_post_like (user_id, post_id) VALUES (?, ?)",
                userId,
                postId
        );
        executeUpdate(
                connection,
                "INSERT INTO community_post_bookmark (user_id, post_id) VALUES (?, ?)",
                userId,
                postId
        );

        assertThatThrownBy(() -> executeUpdate(
                connection,
                "INSERT INTO community_post_like (user_id, post_id) VALUES (?, ?)",
                userId,
                postId
        )).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> executeUpdate(
                connection,
                "INSERT INTO community_post_bookmark (user_id, post_id) VALUES (?, ?)",
                userId,
                postId
        )).isInstanceOf(SQLException.class);
    }

    private Flyway flyway() {
        return Flyway.configure()
                .cleanDisabled(false)
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword()
        );
    }

    private long insertAndReturnId(
            Connection connection,
            String sql,
            Object... parameters
    ) throws SQLException {
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

    private int executeUpdate(
            Connection connection,
            String sql,
            Object... parameters
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setParameters(statement, parameters);
            return statement.executeUpdate();
        }
    }

    private long count(
            Connection connection,
            String sql,
            Object... parameters
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setParameters(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            }
        }
    }

    private String text(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private void setParameters(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }
}
