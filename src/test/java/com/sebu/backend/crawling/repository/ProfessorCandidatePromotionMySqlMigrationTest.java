package com.sebu.backend.crawling.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
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
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class ProfessorCandidatePromotionMySqlMigrationTest {
    private static final LocalDateTime REVIEWED_AT = LocalDateTime.of(2026, 8, 23, 10, 0);
    private static final LocalDateTime PROMOTED_AT = LocalDateTime.of(2026, 8, 23, 11, 0);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Test
    void v14DataSurvivesV15AndV16AndSharedPromotionTargetsAreSupported() throws Exception {
        migrateToV14();

        FixtureIds fixture;
        try (Connection connection = connection()) {
            fixture = insertV14Fixture(connection);
        }

        migrateToV15();

        try (Connection connection = connection()) {
            assertExistingLaboratoryWasBackfilledAsOfficial(connection, fixture.laboratoryId());
            assertApprovedCandidateRemainsUnchangedAndUnpromoted(connection, fixture.candidateId());
            assertInvalidNameSourceIsRejected(connection, fixture.laboratoryId());
            assertNameSourceHasNoDefault(connection);

            markCandidatePromoted(connection, fixture);
        }

        migrateToV16();

        try (Connection connection = connection()) {
            assertExistingAffiliationsWereBackfilled(connection, fixture);
            long sharedCandidateId = insertV16Candidate(connection, fixture.sourceId());
            markCandidatePromoted(connection, fixture, sharedCandidateId);
            assertPromotionTargetsCanBeShared(
                connection,
                fixture.candidateId(),
                sharedCandidateId,
                fixture.professorId(),
                fixture.laboratoryId()
            );
            deleteLaboratory(connection, fixture.laboratoryId());
            assertLaboratoryReferenceWasClearedWithoutLosingPromotionHistory(
                connection,
                fixture.candidateId(),
                fixture.professorId()
            );
            assertLaboratoryReferenceWasClearedWithoutLosingPromotionHistory(
                connection,
                sharedCandidateId,
                fixture.professorId()
            );
            assertLaboratoryAffiliationWasDeleted(connection, fixture.laboratoryId());
        }
    }

    private void migrateToV14() {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("14"))
            .load()
            .migrate();
    }

    private void migrateToV15() {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("15"))
            .load()
            .migrate();
    }

    private void migrateToV16() {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("16"))
            .load()
            .migrate();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
            MYSQL.getJdbcUrl(),
            MYSQL.getUsername(),
            MYSQL.getPassword()
        );
    }

    private FixtureIds insertV14Fixture(Connection connection) throws SQLException {
        long collegeId = insertAndReturnId(
            connection,
            "INSERT INTO college (name) VALUES (?)",
            "승격 마이그레이션 테스트 대학"
        );
        long departmentId = insertAndReturnId(
            connection,
            "INSERT INTO department (college_id, name) VALUES (?, ?)",
            collegeId,
            "승격 마이그레이션 테스트 학과"
        );
        long professorId = insertAndReturnId(
            connection,
            "INSERT INTO professor (department_id, name, position, email) VALUES (?, ?, ?, ?)",
            departmentId,
            "기존교수",
            "교수",
            "existing-professor@example.com"
        );
        long laboratoryId = insertAndReturnId(
            connection,
            """
                INSERT INTO laboratory (
                    professor_id,
                    department_id,
                    name,
                    website_url,
                    description,
                    recruitment_status
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
            professorId,
            departmentId,
            "기존 공식 연구실",
            "https://example.com/existing-lab",
            "기존 연구실 소개",
            "UNKNOWN"
        );
        long sourceId = insertAndReturnId(
            connection,
            """
                INSERT INTO crawl_source (
                    department_id,
                    source_name,
                    source_url,
                    parser_type,
                    is_active,
                    last_crawl_status,
                    version
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
            departmentId,
            "승격 마이그레이션 테스트 교수진",
            "https://example.com/promotion-migration/professors",
            "SEJONG_STANDARD",
            true,
            "SUCCESS",
            0L
        );
        long candidateId = insertAndReturnId(
            connection,
            """
                INSERT INTO professor_crawl_candidate (
                    source_id,
                    source_identity_key,
                    professor_name,
                    position,
                    email,
                    laboratory_name,
                    research_introduction,
                    homepage_url,
                    source_url_at_crawl,
                    parser_type_at_crawl,
                    is_stale,
                    review_status,
                    reviewed_by,
                    reviewed_at,
                    crawled_at,
                    version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            sourceId,
            "email:approved-candidate@example.com",
            "승인후보교수",
            "교수",
            "approved-candidate@example.com",
            null,
            "검수 완료된 연구 소개",
            "https://example.com/approved-candidate",
            "https://example.com/promotion-migration/professors",
            "SEJONG_STANDARD",
            false,
            "APPROVED",
            "migration-tester",
            Timestamp.valueOf(REVIEWED_AT),
            Timestamp.valueOf(REVIEWED_AT.minusHours(1)),
            0L
        );
        return new FixtureIds(professorId, laboratoryId, sourceId, candidateId);
    }

    private void assertExistingLaboratoryWasBackfilledAsOfficial(
        Connection connection,
        long laboratoryId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT name, name_source
            FROM laboratory
            WHERE id = ?
            """)) {
            statement.setLong(1, laboratoryId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("name")).isEqualTo("기존 공식 연구실");
                assertThat(result.getString("name_source")).isEqualTo("OFFICIAL");
            }
        }
    }

    private void assertApprovedCandidateRemainsUnchangedAndUnpromoted(
        Connection connection,
        long candidateId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT review_status,
                   review_revision,
                   laboratory_name,
                   promoted_professor_id,
                   promoted_laboratory_id,
                   promoted_at,
                   promoted_reviewed_at,
                   promoted_review_revision
            FROM professor_crawl_candidate
            WHERE id = ?
            """)) {
            statement.setLong(1, candidateId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("review_status")).isEqualTo("APPROVED");
                assertThat(result.getLong("review_revision")).isOne();
                assertThat(result.getString("laboratory_name")).isNull();
                assertThat(result.getObject("promoted_professor_id")).isNull();
                assertThat(result.getObject("promoted_laboratory_id")).isNull();
                assertThat(result.getTimestamp("promoted_at")).isNull();
                assertThat(result.getTimestamp("promoted_reviewed_at")).isNull();
                assertThat(result.getObject("promoted_review_revision")).isNull();
            }
        }
    }

    private void assertInvalidNameSourceIsRejected(
        Connection connection,
        long laboratoryId
    ) {
        assertThatThrownBy(() -> executeUpdate(
            connection,
            "UPDATE laboratory SET name_source = ? WHERE id = ?",
            "UNKNOWN_SOURCE",
            laboratoryId
        )).isInstanceOf(SQLException.class);
    }

    private void assertNameSourceHasNoDefault(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT column_default
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'laboratory'
              AND column_name = 'name_source'
            """)) {
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("column_default")).isNull();
            }
        }
    }

    private void assertExistingAffiliationsWereBackfilled(
        Connection connection,
        FixtureIds fixture
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT pd.position,
                   COUNT(DISTINCT ld.department_id) AS laboratory_department_count
            FROM professor p
            JOIN professor_department pd
              ON pd.professor_id = p.id
             AND pd.department_id = p.department_id
            JOIN laboratory l ON l.id = ?
            JOIN laboratory_department ld
              ON ld.laboratory_id = l.id
             AND ld.department_id = l.department_id
            WHERE p.id = ?
            GROUP BY pd.position
            """)) {
            statement.setLong(1, fixture.laboratoryId());
            statement.setLong(2, fixture.professorId());
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("position")).isEqualTo("교수");
                assertThat(result.getInt("laboratory_department_count")).isOne();
                assertThat(result.next()).isFalse();
            }
        }
    }

    private long insertV16Candidate(Connection connection, long sourceId) throws SQLException {
        return insertAndReturnId(
            connection,
            """
                INSERT INTO professor_crawl_candidate (
                    source_id,
                    source_identity_key,
                    professor_name,
                    position,
                    email,
                    laboratory_name,
                    research_introduction,
                    homepage_url,
                    source_url_at_crawl,
                    parser_type_at_crawl,
                    is_stale,
                    review_status,
                    reviewed_by,
                    reviewed_at,
                    review_revision,
                    crawled_at,
                    version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            sourceId,
            "email:shared-promotion-target@example.com",
            "겸임후보교수",
            "교수",
            "shared-promotion-target@example.com",
            null,
            "다른 학과에서 검수된 연구 소개",
            "https://example.com/shared-promotion-target",
            "https://example.com/promotion-migration/professors",
            "SEJONG_STANDARD",
            false,
            "APPROVED",
            "migration-tester",
            Timestamp.valueOf(REVIEWED_AT),
            1L,
            Timestamp.valueOf(REVIEWED_AT.minusHours(1)),
            0L
        );
    }

    private void assertPromotionTargetsCanBeShared(
        Connection connection,
        long firstCandidateId,
        long secondCandidateId,
        long professorId,
        long laboratoryId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) AS shared_count
            FROM professor_crawl_candidate
            WHERE id IN (?, ?)
              AND promoted_professor_id = ?
              AND promoted_laboratory_id = ?
            """)) {
            statement.setLong(1, firstCandidateId);
            statement.setLong(2, secondCandidateId);
            statement.setLong(3, professorId);
            statement.setLong(4, laboratoryId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("shared_count")).isEqualTo(2);
            }
        }

        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT index_name, non_unique
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'professor_crawl_candidate'
              AND index_name IN (
                  'idx_professor_crawl_candidate_promoted_professor',
                  'idx_professor_crawl_candidate_promoted_laboratory'
              )
            ORDER BY index_name
            """)) {
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("non_unique")).isOne();
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("non_unique")).isOne();
                assertThat(result.next()).isFalse();
            }
        }
    }

    private void markCandidatePromoted(
        Connection connection,
        FixtureIds fixture
    ) throws SQLException {
        markCandidatePromoted(connection, fixture, fixture.candidateId());
    }

    private void markCandidatePromoted(
        Connection connection,
        FixtureIds fixture,
        long candidateId
    ) throws SQLException {
        int updated = executeUpdate(
            connection,
            """
                UPDATE professor_crawl_candidate
                SET promoted_professor_id = ?,
                    promoted_laboratory_id = ?,
                    promoted_at = ?,
                    promoted_reviewed_at = ?,
                    promoted_review_revision = review_revision
                WHERE id = ?
                """,
            fixture.professorId(),
            fixture.laboratoryId(),
            Timestamp.valueOf(PROMOTED_AT),
            Timestamp.valueOf(REVIEWED_AT),
            candidateId
        );
        assertThat(updated).isOne();
    }

    private void deleteLaboratory(Connection connection, long laboratoryId) throws SQLException {
        int deleted = executeUpdate(
            connection,
            "DELETE FROM laboratory WHERE id = ?",
            laboratoryId
        );
        assertThat(deleted).isOne();
    }

    private void assertLaboratoryReferenceWasClearedWithoutLosingPromotionHistory(
        Connection connection,
        long candidateId,
        long professorId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT review_status,
                   review_revision,
                   laboratory_name,
                   promoted_professor_id,
                   promoted_laboratory_id,
                   promoted_at,
                   promoted_reviewed_at,
                   promoted_review_revision
            FROM professor_crawl_candidate
            WHERE id = ?
            """)) {
            statement.setLong(1, candidateId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("review_status")).isEqualTo("APPROVED");
                assertThat(result.getLong("review_revision")).isOne();
                assertThat(result.getString("laboratory_name")).isNull();
                assertThat(result.getLong("promoted_professor_id")).isEqualTo(professorId);
                assertThat(result.getObject("promoted_laboratory_id")).isNull();
                assertThat(result.getTimestamp("promoted_at")).isNotNull();
                assertThat(result.getTimestamp("promoted_reviewed_at")).isNotNull();
                assertThat(result.getLong("promoted_review_revision")).isOne();
            }
        }
    }

    private void assertLaboratoryAffiliationWasDeleted(
        Connection connection,
        long laboratoryId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*)
            FROM laboratory_department
            WHERE laboratory_id = ?
            """)) {
            statement.setLong(1, laboratoryId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isZero();
            }
        }
    }

    private long insertAndReturnId(
        Connection connection,
        String sql,
        Object... parameters
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            sql,
            Statement.RETURN_GENERATED_KEYS
        )) {
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

    private void setParameters(
        PreparedStatement statement,
        Object... parameters
    ) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    private record FixtureIds(
        long professorId,
        long laboratoryId,
        long sourceId,
        long candidateId
    ) {
    }
}
