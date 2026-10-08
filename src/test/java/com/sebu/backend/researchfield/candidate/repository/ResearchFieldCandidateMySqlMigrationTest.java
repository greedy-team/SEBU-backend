package com.sebu.backend.researchfield.candidate.repository;

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
class ResearchFieldCandidateMySqlMigrationTest {
    private static final LocalDateTime EXTRACTED_AT = LocalDateTime.of(2026, 8, 27, 10, 0);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Test
    void candidateConstraintsAndLaboratoryPurgeCascadeAreEnforcedOnMySql()
        throws Exception {
        migrateToV19();
        long laboratoryId;
        try (Connection connection = connection()) {
            laboratoryId = insertV19Laboratory(connection);
        }

        migrateToV20();

        try (Connection connection = connection()) {
            assertThat(count(
                connection,
                "SELECT COUNT(*) FROM laboratory WHERE id = ?",
                laboratoryId
            )).isOne();
            long candidateId = insertPendingCandidate(connection, laboratoryId);

            assertThatThrownBy(() -> insertPendingCandidate(connection, laboratoryId))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> executeUpdate(
                connection,
                """
                    UPDATE laboratory_research_field_candidate
                    SET review_status = 'APPROVED'
                    WHERE id = ?
                    """,
                candidateId
            )).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> executeUpdate(
                connection,
                """
                    UPDATE laboratory_research_field_candidate
                    SET candidate_name = NULL,
                        review_status = 'APPROVED',
                        reviewed_by = 'migration-reviewer',
                        reviewed_at = ?,
                        review_revision = 1
                    WHERE id = ?
                    """,
                Timestamp.valueOf(EXTRACTED_AT.plusHours(1)),
                candidateId
            )).isInstanceOf(SQLException.class);

            assertThat(executeUpdate(
                connection,
                """
                    UPDATE laboratory_research_field_candidate
                    SET review_status = 'APPROVED',
                        reviewed_by = 'migration-reviewer',
                        reviewed_at = ?,
                        review_revision = 1
                    WHERE id = ?
                    """,
                Timestamp.valueOf(EXTRACTED_AT.plusHours(1)),
                candidateId
            )).isOne();

            migrateToV21();
            long sourceId = insertLongTextSource(connection, laboratoryId);
            assertThatThrownBy(() -> insertManualSplitCandidate(
                connection,
                laboratoryId,
                Long.MAX_VALUE,
                "d".repeat(64)
            )).isInstanceOf(SQLException.class);
            long splitId = insertManualSplitCandidate(
                connection,
                laboratoryId,
                sourceId,
                "d".repeat(64)
            );
            assertThat(count(
                connection,
                """
                    SELECT COUNT(*)
                    FROM laboratory_research_field_candidate
                    WHERE id = ?
                      AND extraction_method = 'MANUAL_SPLIT'
                      AND split_from_candidate_id = ?
                    """,
                splitId,
                sourceId
            )).isOne();
            assertThatThrownBy(() -> executeUpdate(
                connection,
                "DELETE FROM laboratory_research_field_candidate WHERE id = ?",
                sourceId
            )).isInstanceOf(SQLException.class);

            migrateToLatest();
            executeUpdate(connection, "DELETE FROM laboratory WHERE id = ?", laboratoryId);
            assertThat(count(
                connection,
                "SELECT COUNT(*) FROM laboratory_research_field_candidate WHERE laboratory_id = ?",
                laboratoryId
            )).isZero();
        }
    }

    private void migrateToV19() {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("19"))
            .load()
            .migrate();
    }

    private void migrateToV20() {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("20"))
            .load()
            .migrate();
    }

    private void migrateToV21() {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("21"))
            .load()
            .migrate();
    }

    private void migrateToLatest() {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
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

    private long insertV19Laboratory(Connection connection) throws SQLException {
        long collegeId = insertAndReturnId(
            connection,
            "INSERT INTO college (name) VALUES (?)",
            "V20 마이그레이션 대학"
        );
        long departmentId = insertAndReturnId(
            connection,
            "INSERT INTO department (college_id, name) VALUES (?, ?)",
            collegeId,
            "V20 마이그레이션 학과"
        );
        long professorId = insertAndReturnId(
            connection,
            "INSERT INTO professor (department_id, name, email) VALUES (?, ?, ?)",
            departmentId,
            "V20 마이그레이션 교수",
            "v20-migration@example.com"
        );
        return insertAndReturnId(
            connection,
            """
                INSERT INTO laboratory (
                    professor_id,
                    department_id,
                    name,
                    description,
                    recruitment_status,
                    name_source
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
            professorId,
            departmentId,
            "V20 마이그레이션 연구실",
            "인공지능, 로보틱스",
            "UNKNOWN",
            "GENERATED"
        );
    }

    private long insertPendingCandidate(
        Connection connection,
        long laboratoryId
    ) throws SQLException {
        return insertAndReturnId(
            connection,
            """
                INSERT INTO laboratory_research_field_candidate (
                    laboratory_id,
                    source_field_key,
                    source_description_hash,
                    raw_field_text,
                    candidate_name,
                    extraction_method,
                    source_order,
                    extraction_rule_version,
                    extracted_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            laboratoryId,
            "a".repeat(64),
            "b".repeat(64),
            "인공지능",
            "인공지능",
            "DELIMITED",
            0,
            "sejong-v1",
            Timestamp.valueOf(EXTRACTED_AT)
        );
    }

    private long insertLongTextSource(
        Connection connection,
        long laboratoryId
    ) throws SQLException {
        return insertAndReturnId(
            connection,
            """
                INSERT INTO laboratory_research_field_candidate (
                    laboratory_id,
                    source_field_key,
                    source_description_hash,
                    raw_field_text,
                    candidate_name,
                    extraction_method,
                    source_order,
                    extraction_rule_version,
                    extracted_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            laboratoryId,
            "c".repeat(64),
            "b".repeat(64),
            "자율주행자동차와 드론의 환경 인식 및 제어를 연구합니다.",
            null,
            "LONG_TEXT",
            1,
            "sejong-v1",
            Timestamp.valueOf(EXTRACTED_AT)
        );
    }

    private long insertManualSplitCandidate(
        Connection connection,
        long laboratoryId,
        Long sourceId,
        String sourceFieldKey
    ) throws SQLException {
        return insertAndReturnId(
            connection,
            """
                INSERT INTO laboratory_research_field_candidate (
                    laboratory_id,
                    split_from_candidate_id,
                    source_field_key,
                    source_description_hash,
                    raw_field_text,
                    candidate_name,
                    extraction_method,
                    source_order,
                    extraction_rule_version,
                    extracted_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            laboratoryId,
            sourceId,
            sourceFieldKey,
            "b".repeat(64),
            "자율주행 인공지능",
            "자율주행 인공지능",
            "MANUAL_SPLIT",
            1,
            "manual-split-csv-v1",
            Timestamp.valueOf(EXTRACTED_AT)
        );
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

    private long count(
        Connection connection,
        String sql,
        Object... parameters
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setParameters(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
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
}
