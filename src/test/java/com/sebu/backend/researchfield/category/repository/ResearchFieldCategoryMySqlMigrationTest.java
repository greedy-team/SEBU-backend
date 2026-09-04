package com.sebu.backend.researchfield.category.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class ResearchFieldCategoryMySqlMigrationTest {
    private static final int EXPECTED_CATEGORY_COUNT = 21;
    private static final int EXPECTED_BASE_REFERENCE_COUNT = 535;
    private static final int EXPECTED_NATURAL_SCIENCE_REFERENCE_COUNT = 166;
    private static final String REFERENCE_ONLY_BASE_FIELD = "XAI 등";
    private static final String REFERENCE_ONLY_NATURAL_SCIENCE_FIELD = "NMR 분광학";
    private static final String INVALID_NUMBERED_LIST_FIELD = "1";
    private static final Map<String, String> NATURAL_SCIENCE_FIELD_NAME_CORRECTIONS = Map.of(
        "고분자 화학 (고분자 합성) 4",
        "고분자 화학 (고분자 합성)",
        "다양한 응용 분야를 위한 그래핀-고분자 복합재료 개발 2",
        "다양한 응용 분야를 위한 그래핀-고분자 복합재료 개발",
        "빌량분석법",
        "질량분석법",
        "원자력 현미경(AFM)을 이용한 접착력 연구 3",
        "원자힘 현미경(AFM)을 이용한 접착력 연구",
        "폐수 중금속 이온 흡착 및 약물 전달을 위한 젤라틴 기반 하이드로겔 입자 제조 5",
        "폐수 중금속 이온 흡착 및 약물 전달을 위한 젤라틴 기반 하이드로겔 입자 제조"
    );
    private static final Path BASE_CLASSIFICATION_CSV = Path.of(
        "docs",
        "data",
        "research-field-category-classification.csv"
    );
    private static final Path NATURAL_SCIENCE_MAPPING_MIGRATION = Path.of(
        "src",
        "main",
        "resources",
        "db",
        "migration",
        "V33__seed_natural_science_research_field_category_mappings.sql"
    );
    private static final Pattern SQL_ASSIGNMENT_PATTERN = Pattern.compile(
        "^\\s*\\('(.+)', '([A-Z_]+)'\\)[,;]$"
    );

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @BeforeEach
    void cleanDatabase() {
        flyway(null).clean();
    }

    @Test
    void blankDatabaseCreatesCategoriesWithoutInventingResearchFields() throws Exception {
        flyway(null).migrate();

        try (Connection connection = connection()) {
            assertThat(count(connection, "SELECT COUNT(*) FROM research_field_category"))
                .isEqualTo(EXPECTED_CATEGORY_COUNT);
            assertThat(count(connection, "SELECT COUNT(*) FROM research_field"))
                .isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM research_field_category_mapping"))
                .isZero();
            assertThat(count(
                connection,
                "SELECT COUNT(DISTINCT code) FROM research_field_category"
            )).isEqualTo(EXPECTED_CATEGORY_COUNT);
            assertThat(count(
                connection,
                "SELECT COUNT(DISTINCT name) FROM research_field_category"
            )).isEqualTo(EXPECTED_CATEGORY_COUNT);
            assertThat(count(
                connection,
                "SELECT COUNT(DISTINCT display_order) FROM research_field_category"
            )).isEqualTo(EXPECTED_CATEGORY_COUNT);

            assertThatThrownBy(() -> executeUpdate(
                connection,
                """
                    INSERT INTO research_field_category (
                        code,
                        name,
                        description,
                        display_order
                    ) VALUES (?, ?, ?, ?)
                    """,
                "AI_ML",
                "중복 카테고리",
                "중복 코드는 허용되지 않는다",
                99
            )).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void v31UpgradeMapsOnlyExistingNaturalScienceFields() throws Exception {
        flyway("31").migrate();
        List<CuratedAssignment> naturalScienceAssignments = readNaturalScienceAssignments();
        assertThat(naturalScienceAssignments)
            .hasSize(EXPECTED_NATURAL_SCIENCE_REFERENCE_COUNT);
        Map<String, Set<String>> naturalScienceAssignmentMap = expectedAssignments(
            naturalScienceAssignments
        );
        assertThat(naturalScienceAssignmentMap)
            .containsEntry("이미지 처리", Set.of("SIGNAL_MEDIA"))
            .containsEntry(
                REFERENCE_ONLY_NATURAL_SCIENCE_FIELD,
                Set.of("CHEMISTRY_MATERIALS")
            );
        List<String> existingNaturalScienceFieldNames = naturalScienceAssignmentMap
            .keySet()
            .stream()
            .filter(name -> !name.equals(REFERENCE_ONLY_NATURAL_SCIENCE_FIELD))
            .toList();
        Map<String, Set<String>> expectedExistingAssignments = new LinkedHashMap<>(
            canonicalNaturalScienceAssignments(naturalScienceAssignmentMap)
        );
        expectedExistingAssignments.remove(REFERENCE_ONLY_NATURAL_SCIENCE_FIELD);

        long existingImageProcessingId;
        long customFieldId;
        long fieldCountBeforeUpgrade;
        try (Connection connection = connection()) {
            assertThat(count(connection, "SELECT COUNT(*) FROM research_field"))
                .isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM research_field_category_mapping"))
                .isZero();
            insertFields(connection, existingNaturalScienceFieldNames);
            customFieldId = insertAndReturnId(
                connection,
                "INSERT INTO research_field (name) VALUES (?)",
                "V31 기존 사용자 정의 연구 분야"
            );
            existingImageProcessingId = findFieldId(connection, "이미지 처리");
            assertThat(executeUpdate(
                connection,
                """
                    INSERT INTO research_field_category_mapping (research_field_id, category_id)
                    SELECT field.id, category.id
                    FROM research_field field
                    JOIN research_field_category category
                      ON category.code = ?
                    WHERE field.name = ?
                    """,
                "SIGNAL_MEDIA",
                "이미지 처리"
            )).isOne();
            fieldCountBeforeUpgrade = count(connection, "SELECT COUNT(*) FROM research_field");
            assertThat(count(connection, "SELECT COUNT(*) FROM research_field_category"))
                .isEqualTo(18L);
            assertThat(count(connection, "SELECT COUNT(*) FROM research_field_category_mapping"))
                .isOne();
        }

        flyway(null).migrate();

        try (Connection connection = connection()) {
            assertThat(findFieldId(connection, "이미지 처리"))
                .isEqualTo(existingImageProcessingId);
            assertThat(count(connection, "SELECT COUNT(*) FROM research_field_category"))
                .isEqualTo(EXPECTED_CATEGORY_COUNT);
            assertThat(count(connection, "SELECT COUNT(*) FROM research_field"))
                .isEqualTo(fieldCountBeforeUpgrade - 1);
            assertThat(count(connection, "SELECT COUNT(*) FROM research_field_category_mapping"))
                .isEqualTo(mappingCount(expectedExistingAssignments));
            assertMapping(connection, "이미지 처리", "SIGNAL_MEDIA");
            assertMapping(connection, "가사이드 이론", "MATH_STATISTICS");
            assertMapping(connection, "우주론", "PHYSICS_ASTRONOMY");
            assertMapping(connection, "고분자 열역학", "CHEMISTRY_MATERIALS");
            assertMapping(connection, "질량분석법", "CHEMISTRY_MATERIALS");
            assertThat(count(
                connection,
                "SELECT COUNT(*) FROM research_field WHERE name = ?",
                INVALID_NUMBERED_LIST_FIELD
            )).isZero();
            assertThat(count(
                connection,
                "SELECT COUNT(*) FROM research_field WHERE name = ?",
                REFERENCE_ONLY_NATURAL_SCIENCE_FIELD
            )).isZero();
            assertThat(count(
                connection,
                """
                    SELECT COUNT(*)
                    FROM research_field_category_mapping
                    WHERE research_field_id = ?
                    """,
                customFieldId
            )).isZero();
            assertThat(findAllMappings(connection))
                .isEqualTo(expectedExistingAssignments);
        }
    }

    @Test
    void v33UpgradeCorrectsNamesMergesDuplicatesAndRejectsNumberArtifacts()
        throws Exception {
        flyway("33").migrate();

        long laboratoryId;
        long misspelledFieldId;
        long canonicalFieldId;
        long trailingNumberFieldId;
        long numberArtifactFieldId;
        long misspelledCandidateId;
        long trailingNumberCandidateId;
        long numberArtifactCandidateId;
        long validCandidateWithInvalidPromotionId;
        try (Connection connection = connection()) {
            laboratoryId = insertLaboratoryFixture(connection);
            misspelledFieldId = insertAndReturnId(
                connection,
                "INSERT INTO research_field (name) VALUES (?)",
                "빌량분석법"
            );
            canonicalFieldId = insertAndReturnId(
                connection,
                "INSERT INTO research_field (name) VALUES (?)",
                "질량분석법"
            );
            trailingNumberFieldId = insertAndReturnId(
                connection,
                "INSERT INTO research_field (name) VALUES (?)",
                "고분자 화학 (고분자 합성) 4"
            );
            numberArtifactFieldId = insertAndReturnId(
                connection,
                "INSERT INTO research_field (name) VALUES (?)",
                INVALID_NUMBERED_LIST_FIELD
            );

            insertLaboratoryFieldLink(connection, laboratoryId, misspelledFieldId);
            insertLaboratoryFieldLink(connection, laboratoryId, canonicalFieldId);
            insertLaboratoryFieldLink(connection, laboratoryId, trailingNumberFieldId);
            insertLaboratoryFieldLink(connection, laboratoryId, numberArtifactFieldId);
            insertCategoryMapping(connection, misspelledFieldId, "CHEMISTRY_MATERIALS");
            insertCategoryMapping(connection, canonicalFieldId, "CHEMISTRY_MATERIALS");
            insertCategoryMapping(connection, canonicalFieldId, "BIOMED_HEALTH");
            insertCategoryMapping(connection, trailingNumberFieldId, "CHEMISTRY_MATERIALS");
            insertCategoryMapping(connection, numberArtifactFieldId, "CHEMISTRY_MATERIALS");

            misspelledCandidateId = insertPromotedCandidate(
                connection,
                laboratoryId,
                "a".repeat(64),
                "빌량분석법",
                misspelledFieldId,
                0
            );
            trailingNumberCandidateId = insertPromotedCandidate(
                connection,
                laboratoryId,
                "b".repeat(64),
                "고분자 화학 (고분자 합성) 4",
                trailingNumberFieldId,
                1
            );
            numberArtifactCandidateId = insertPromotedCandidate(
                connection,
                laboratoryId,
                "c".repeat(64),
                INVALID_NUMBERED_LIST_FIELD,
                numberArtifactFieldId,
                2
            );
            validCandidateWithInvalidPromotionId = insertPromotedCandidate(
                connection,
                laboratoryId,
                "e".repeat(64),
                "검수된 유효 연구 분야",
                numberArtifactFieldId,
                3
            );
            assertThat(executeUpdate(
                connection,
                """
                    UPDATE laboratory_research_field_candidate
                    SET review_note = '기존 검수 메모',
                        reviewed_by = 'reviewer-before-v34',
                        reviewed_at = '2026-09-01 12:34:56',
                        review_revision = 7,
                        promoted_reviewed_at = '2026-09-01 12:34:56',
                        promoted_review_revision = 7
                    WHERE id = ?
                    """,
                validCandidateWithInvalidPromotionId
            )).isOne();
        }

        flyway(null).migrate();

        try (Connection connection = connection()) {
            assertThat(count(
                connection,
                "SELECT COUNT(*) FROM research_field WHERE id = ?",
                misspelledFieldId
            )).isZero();
            assertThat(findFieldId(connection, "질량분석법")).isEqualTo(canonicalFieldId);
            assertThat(count(
                connection,
                """
                    SELECT COUNT(*)
                    FROM laboratory_research_field
                    WHERE laboratory_id = ?
                      AND research_field_id = ?
                    """,
                laboratoryId,
                canonicalFieldId
            )).isOne();
            assertThat(count(
                connection,
                """
                    SELECT COUNT(*)
                    FROM laboratory_research_field_candidate
                    WHERE id = ?
                      AND candidate_name = '질량분석법'
                      AND raw_field_text = '빌량분석법'
                      AND promoted_research_field_id = ?
                    """,
                misspelledCandidateId,
                canonicalFieldId
            )).isOne();
            assertMapping(connection, "질량분석법", "CHEMISTRY_MATERIALS");
            assertMapping(connection, "질량분석법", "BIOMED_HEALTH");
            assertThat(findAllMappings(connection))
                .containsEntry(
                    "질량분석법",
                    Set.of("CHEMISTRY_MATERIALS", "BIOMED_HEALTH")
                );
            assertThat(count(
                connection,
                """
                    SELECT COUNT(*)
                    FROM research_field_category_mapping
                    WHERE research_field_id = ?
                    """,
                canonicalFieldId
            )).isEqualTo(2L);

            assertThat(findFieldId(connection, "고분자 화학 (고분자 합성)"))
                .isEqualTo(trailingNumberFieldId);
            assertThat(count(
                connection,
                "SELECT COUNT(*) FROM research_field WHERE name = ?",
                "고분자 화학 (고분자 합성) 4"
            )).isZero();
            assertThat(count(
                connection,
                """
                    SELECT COUNT(*)
                    FROM laboratory_research_field_candidate
                    WHERE id = ?
                      AND candidate_name = '고분자 화학 (고분자 합성)'
                      AND promoted_research_field_id = ?
                    """,
                trailingNumberCandidateId,
                trailingNumberFieldId
            )).isOne();
            assertMapping(
                connection,
                "고분자 화학 (고분자 합성)",
                "CHEMISTRY_MATERIALS"
            );

            assertThat(count(
                connection,
                "SELECT COUNT(*) FROM research_field WHERE id = ?",
                numberArtifactFieldId
            )).isZero();
            assertThat(count(
                connection,
                """
                    SELECT COUNT(*)
                    FROM laboratory_research_field
                    WHERE research_field_id = ?
                    """,
                numberArtifactFieldId
            )).isZero();
            assertThat(count(
                connection,
                """
                    SELECT COUNT(*)
                    FROM research_field_category_mapping
                    WHERE research_field_id = ?
                    """,
                numberArtifactFieldId
            )).isZero();
            assertThat(count(
                connection,
                """
                    SELECT COUNT(*)
                    FROM laboratory_research_field_candidate
                    WHERE id = ?
                      AND raw_field_text = '1'
                      AND candidate_name IS NULL
                      AND review_status = 'REJECTED'
                      AND reviewed_by = 'flyway-v34'
                      AND promoted_research_field_id IS NULL
                      AND promoted_at IS NULL
                      AND promoted_reviewed_at IS NULL
                      AND promoted_review_revision IS NULL
                    """,
                numberArtifactCandidateId
            )).isOne();
            assertThat(count(
                connection,
                """
                    SELECT COUNT(*)
                    FROM laboratory_research_field_candidate
                    WHERE id = ?
                      AND raw_field_text = '검수된 유효 연구 분야'
                      AND candidate_name = '검수된 유효 연구 분야'
                      AND review_status = 'APPROVED'
                      AND review_note = '기존 검수 메모'
                      AND reviewed_by = 'reviewer-before-v34'
                      AND reviewed_at = '2026-09-01 12:34:56'
                      AND review_revision = 7
                      AND promoted_research_field_id IS NULL
                      AND promoted_at IS NULL
                      AND promoted_reviewed_at IS NULL
                      AND promoted_review_revision IS NULL
                    """,
                validCandidateWithInvalidPromotionId
            )).isOne();
        }
    }

    @Test
    void v22UpgradeMapsExistingCuratedFieldsWithoutCreatingReferenceOnlyFields()
        throws Exception {
        flyway("22").migrate();
        List<CuratedAssignment> baseAssignments = readBaseAssignments();
        assertThat(baseAssignments).hasSize(EXPECTED_BASE_REFERENCE_COUNT);
        Map<String, Set<String>> baseAssignmentMap = expectedAssignments(baseAssignments);
        assertThat(baseAssignmentMap)
            .containsEntry(REFERENCE_ONLY_BASE_FIELD, Set.of("AI_ML"));
        List<String> baseFieldNames = baseAssignmentMap
            .keySet()
            .stream()
            .filter(name -> !name.equals(REFERENCE_ONLY_BASE_FIELD))
            .toList();
        Map<String, Set<String>> expectedExistingAssignments = new LinkedHashMap<>(
            baseAssignmentMap
        );
        expectedExistingAssignments.remove(REFERENCE_ONLY_BASE_FIELD);

        long existingSeedFieldId;
        long customFieldId;
        long fieldCountBeforeUpgrade;
        try (Connection connection = connection()) {
            insertFields(connection, baseFieldNames);
            existingSeedFieldId = findFieldId(connection, "인공지능");
            customFieldId = insertAndReturnId(
                connection,
                "INSERT INTO research_field (name) VALUES (?)",
                "V22 기존 사용자 정의 연구 분야"
            );
            fieldCountBeforeUpgrade = count(connection, "SELECT COUNT(*) FROM research_field");
        }

        flyway(null).migrate();

        try (Connection connection = connection()) {
            assertThat(findFieldId(connection, "인공지능")).isEqualTo(existingSeedFieldId);
            assertThat(findFieldId(connection, "V22 기존 사용자 정의 연구 분야"))
                .isEqualTo(customFieldId);
            assertThat(count(connection, "SELECT COUNT(*) FROM research_field"))
                .isEqualTo(fieldCountBeforeUpgrade);
            assertThat(count(connection, "SELECT COUNT(*) FROM research_field_category_mapping"))
                .isEqualTo(mappingCount(expectedExistingAssignments));
            assertThat(count(
                connection,
                "SELECT COUNT(DISTINCT research_field_id) FROM research_field_category_mapping"
            )).isEqualTo(baseFieldNames.size());
            assertThat(count(
                connection,
                """
                    SELECT COUNT(*)
                    FROM research_field_category category
                    LEFT JOIN research_field_category_mapping mapping
                      ON mapping.category_id = category.id
                    WHERE mapping.category_id IS NULL
                    """
            )).isEqualTo(3L);
            assertThat(count(
                connection,
                """
                    SELECT COUNT(*)
                    FROM research_field_category_mapping
                    WHERE research_field_id = ?
                    """,
                customFieldId
            )).isZero();
            assertMapping(connection, "인공지능", "AI_ML");
            assertMapping(connection, "의료 인공지능", "BIOMED_HEALTH");
            assertMapping(connection, "양자 컴퓨팅 알고리즘 (quantum algorithm)", "QUANTUM_TECH");
            assertMapping(connection, "메타버스 보안", "SECURITY_CRYPTO");
            assertMapping(connection, "로봇공학", "ROBOT_AUTONOMOUS");
            assertMapping(connection, "5G/6G 시스템", "COMM_NETWORK");
            assertThat(count(
                connection,
                "SELECT COUNT(*) FROM research_field WHERE name = ?",
                REFERENCE_ONLY_BASE_FIELD
            )).isZero();
            assertThat(count(
                connection,
                "SELECT COUNT(*) FROM research_field WHERE name = ?",
                REFERENCE_ONLY_NATURAL_SCIENCE_FIELD
            )).isZero();
            assertThat(findAllMappings(connection))
                .isEqualTo(expectedExistingAssignments);
            assertThatThrownBy(() -> executeUpdate(
                connection,
                "DELETE FROM research_field_category WHERE code = ?",
                "AI_ML"
            )).isInstanceOf(SQLException.class);
        }
    }

    private List<CuratedAssignment> readBaseAssignments() throws Exception {
        return readCuratedAssignments(BASE_CLASSIFICATION_CSV, 0, 5);
    }

    private List<CuratedAssignment> readNaturalScienceAssignments() throws Exception {
        return Files.readAllLines(NATURAL_SCIENCE_MAPPING_MIGRATION, StandardCharsets.UTF_8)
            .stream()
            .map(SQL_ASSIGNMENT_PATTERN::matcher)
            .filter(Matcher::matches)
            .map(matcher -> new CuratedAssignment(matcher.group(1), matcher.group(2)))
            .toList();
    }

    private List<CuratedAssignment> readCuratedAssignments(
        Path csvPath,
        int fieldNameColumn,
        int categoryCodeColumn
    ) throws Exception {
        return Files.readAllLines(csvPath, StandardCharsets.UTF_8).stream()
            .skip(1)
            .map(this::parseCsvRow)
            .map(columns -> new CuratedAssignment(
                columns.get(fieldNameColumn),
                columns.get(categoryCodeColumn)
            ))
            .toList();
    }

    private List<String> parseCsvRow(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (quoted && current == '\"') {
                if (index + 1 < line.length() && line.charAt(index + 1) == '\"') {
                    value.append('\"');
                    index++;
                } else {
                    quoted = false;
                }
                continue;
            }
            if (quoted) {
                value.append(current);
                continue;
            }
            if (current == '\"' && value.isEmpty()) {
                quoted = true;
                continue;
            }
            if (current == ',') {
                columns.add(value.toString());
                value.setLength(0);
                continue;
            }
            value.append(current);
        }
        if (quoted) {
            throw new IllegalArgumentException("Quoted CSV value is not closed: " + line);
        }
        columns.add(value.toString());
        if (columns.size() < 6) {
            throw new IllegalArgumentException("CSV columns are missing: " + line);
        }
        return columns;
    }

    private Map<String, Set<String>> expectedAssignments(
        List<CuratedAssignment> assignments
    ) {
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        for (CuratedAssignment assignment : assignments) {
            expected.computeIfAbsent(
                assignment.researchFieldName(),
                ignored -> new LinkedHashSet<>()
            ).add(assignment.categoryCode());
        }
        return expected;
    }

    private Map<String, Set<String>> canonicalNaturalScienceAssignments(
        Map<String, Set<String>> assignments
    ) {
        Map<String, Set<String>> canonicalAssignments = new LinkedHashMap<>();
        assignments.forEach((fieldName, categoryCodes) -> {
            if (fieldName.equals(INVALID_NUMBERED_LIST_FIELD)) {
                return;
            }
            String canonicalName = NATURAL_SCIENCE_FIELD_NAME_CORRECTIONS.getOrDefault(
                fieldName,
                fieldName
            );
            canonicalAssignments.computeIfAbsent(
                canonicalName,
                ignored -> new LinkedHashSet<>()
            ).addAll(categoryCodes);
        });
        return canonicalAssignments;
    }

    private Map<String, Set<String>> findAllMappings(Connection connection) throws SQLException {
        Map<String, Set<String>> mappings = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
            """
                SELECT field.name, category.code
                FROM research_field_category_mapping mapping
                JOIN research_field field
                  ON field.id = mapping.research_field_id
                JOIN research_field_category category
                  ON category.id = mapping.category_id
                ORDER BY field.name
                """
        ); ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String fieldName = result.getString("name");
                mappings.computeIfAbsent(
                    fieldName,
                    ignored -> new LinkedHashSet<>()
                ).add(result.getString("code"));
            }
        }
        return mappings;
    }

    private long mappingCount(Map<String, Set<String>> assignments) {
        return assignments.values().stream()
            .mapToLong(Set::size)
            .sum();
    }

    private void insertFields(Connection connection, List<String> names) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO research_field (name) VALUES (?)"
        )) {
            for (String name : names) {
                statement.setString(1, name);
                statement.addBatch();
            }
            assertThat(statement.executeBatch()).hasSize(names.size());
        }
    }

    private long insertLaboratoryFixture(Connection connection) throws SQLException {
        long collegeId = insertAndReturnId(
            connection,
            "INSERT INTO college (name) VALUES (?)",
            "V34 마이그레이션 테스트 단과대"
        );
        long departmentId = insertAndReturnId(
            connection,
            "INSERT INTO department (college_id, name) VALUES (?, ?)",
            collegeId,
            "V34 마이그레이션 테스트 학과"
        );
        long professorId = insertAndReturnId(
            connection,
            "INSERT INTO professor (department_id, name) VALUES (?, ?)",
            departmentId,
            "V34 마이그레이션 테스트 교수"
        );
        return insertAndReturnId(
            connection,
            """
                INSERT INTO laboratory (
                    professor_id,
                    department_id,
                    name,
                    recruitment_status,
                    name_source
                ) VALUES (?, ?, ?, 'UNKNOWN', 'GENERATED')
                """,
            professorId,
            departmentId,
            "V34 마이그레이션 테스트 연구실"
        );
    }

    private void insertLaboratoryFieldLink(
        Connection connection,
        long laboratoryId,
        long researchFieldId
    ) throws SQLException {
        assertThat(executeUpdate(
            connection,
            """
                INSERT INTO laboratory_research_field (laboratory_id, research_field_id)
                VALUES (?, ?)
                """,
            laboratoryId,
            researchFieldId
        )).isOne();
    }

    private void insertCategoryMapping(
        Connection connection,
        long researchFieldId,
        String categoryCode
    ) throws SQLException {
        assertThat(executeUpdate(
            connection,
            """
                INSERT INTO research_field_category_mapping (research_field_id, category_id)
                SELECT ?, category.id
                FROM research_field_category category
                WHERE category.code = ?
                """,
            researchFieldId,
            categoryCode
        )).isOne();
    }

    private long insertPromotedCandidate(
        Connection connection,
        long laboratoryId,
        String sourceFieldKey,
        String candidateName,
        long promotedResearchFieldId,
        int sourceOrder
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
                    is_stale,
                    review_status,
                    reviewed_by,
                    reviewed_at,
                    review_revision,
                    promoted_research_field_id,
                    promoted_at,
                    promoted_reviewed_at,
                    promoted_review_revision,
                    extracted_at
                ) VALUES (
                    ?, ?, ?, ?, ?, 'DELIMITED', ?, 'sejong-v1', FALSE,
                    'APPROVED', 'migration-test', CURRENT_TIMESTAMP, 1, ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP
                )
                """,
            laboratoryId,
            sourceFieldKey,
            "d".repeat(64),
            candidateName,
            candidateName,
            sourceOrder,
            promotedResearchFieldId
        );
    }

    private Flyway flyway(String target) {
        FluentConfiguration configuration = Flyway.configure()
            .cleanDisabled(false)
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(MigrationVersion.fromVersion(target));
        }
        return configuration.load();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
            MYSQL.getJdbcUrl(),
            MYSQL.getUsername(),
            MYSQL.getPassword()
        );
    }

    private void assertMapping(
        Connection connection,
        String researchFieldName,
        String categoryCode
    ) throws SQLException {
        assertThat(count(
            connection,
            """
                SELECT COUNT(*)
                FROM research_field_category_mapping mapping
                JOIN research_field field
                  ON field.id = mapping.research_field_id
                JOIN research_field_category category
                  ON category.id = mapping.category_id
                WHERE field.name = ?
                  AND category.code = ?
                """,
            researchFieldName,
            categoryCode
        )).isOne();
    }

    private long findFieldId(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT id FROM research_field WHERE name = ?"
        )) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong("id");
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

    private record CuratedAssignment(String researchFieldName, String categoryCode) {
    }
}
