package com.sebu.backend.laboratory.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AerospaceLaboratoryLinksMigrationContract {
    private static final String MIGRATION = "db/migration/V51__add_missing_aerospace_laboratory_links.sql";
    private static final List<Link> LINKS = readLinks();
    private JdbcTemplate jdbc;

    protected abstract DataSource dataSource();

    @BeforeEach
    void prepareDedicatedDatabase() {
        jdbc = new JdbcTemplate(dataSource());
        flyway("51").clean();
    }

    @Test
    void blankDatabaseMigratesThroughAllVersionsAndValidatesHibernate() {
        assertThat(flyway("51").migrate().migrationsExecuted).isPositive();

        for (Link link : LINKS) {
            var fixture = fixture(link);
            assertThat(jdbc.queryForMap("SELECT * FROM laboratory WHERE id=?", fixture.laboratoryId()))
                .containsEntry("website_url", link.newUrl())
                .containsEntry("website_url_source", "MANUAL");
        }
        flyway("51").validate();
        assertThat(flyway("51").migrate().migrationsExecuted).isZero();
        validateHibernate();
    }

    @Test
    void upgradeChangesOnlyVerifiedLinksAndPreservesIdentityReviewsBookmarksResearchAndAffiliations() {
        flyway("50").migrate();
        var fixtures = LINKS.stream().map(this::fixture).toList();
        for (int index = 0; index < fixtures.size(); index++) {
            var fixture = fixtures.get(index);
            assertThat(jdbc.queryForMap("SELECT * FROM laboratory WHERE id=?", fixture.laboratoryId()))
                .containsEntry("website_url", null).containsEntry("website_url_source", null);
            jdbc.update("UPDATE laboratory SET updated_at='2001-01-01 00:00:00' WHERE id=?", fixture.laboratoryId());
            addPreservedUserData(fixture, 9000 + index);
        }
        var expected = snapshotData();

        assertThat(flyway("51").migrate().migrationsExecuted).isOne();

        fixtures.forEach(fixture -> expectOnlyLinkChange(expected, fixture));
        assertThat(snapshotData()).isEqualTo(expected);
        flyway("51").validate();
        assertThat(flyway("51").migrate().migrationsExecuted).isZero();

        // Replaying the SQL must preserve the previously filled links and timestamps.
        fixtures.forEach(fixture -> jdbc.update(
            "UPDATE laboratory SET updated_at='2002-01-01 00:00:00' WHERE id=?", fixture.laboratoryId()));
        var beforeRetry = snapshotData();
        var retry = new ResourceDatabasePopulator(new ClassPathResource(MIGRATION));
        retry.setSqlScriptEncoding("UTF-8");
        retry.execute(dataSource());
        assertThat(snapshotData()).isEqualTo(beforeRetry);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANUAL", "CRAWLED"})
    void existingWebsiteIsNotOverwritten(String source) {
        var fixture = prepareSelectedTarget(LINKS.getFirst());
        jdbc.update("UPDATE laboratory SET website_url=?,website_url_source=? WHERE id=?",
            "https://example.com/reviewed-official-lab", source, fixture.laboratoryId());
        assertUpgradeLeavesDataUnchanged();
    }

    @Test
    void deletedLaboratoryIsNotRestoredOrUpdated() {
        var fixture = prepareSelectedTarget(LINKS.getFirst());
        jdbc.update("UPDATE laboratory SET deleted_at='2002-01-01 00:00:00' WHERE id=?", fixture.laboratoryId());
        assertUpgradeLeavesDataUnchanged();
    }

    @Test
    void namesakeWithDifferentEmailIsNotUpdated() {
        var fixture = prepareSelectedTarget(LINKS.getFirst());
        jdbc.update("UPDATE professor SET email='namesake@example.com' WHERE id=?", fixture.professorId());
        assertUpgradeLeavesDataUnchanged();
    }

    @Test
    void mismatchedProfessorNameIsNotUpdated() {
        var fixture = prepareSelectedTarget(LINKS.getFirst());
        jdbc.update("UPDATE professor SET name='다른 교수' WHERE id=?", fixture.professorId());
        assertUpgradeLeavesDataUnchanged();
    }

    @Test
    void laboratoryWithDifferentNameIsNotUpdated() {
        var fixture = prepareSelectedTarget(LINKS.getFirst());
        jdbc.update("UPDATE laboratory SET name='별도로 검수한 연구실' WHERE id=?", fixture.laboratoryId());
        assertUpgradeLeavesDataUnchanged();
    }

    @Test
    void sameDepartmentNameInDifferentCollegeIsNotUpdated() {
        var fixture = prepareSelectedTarget(LINKS.getFirst());
        jdbc.update("INSERT INTO department (id,college_id,name) SELECT 9006,id,? FROM college WHERE name='자연과학대학'",
            fixture.link().allowedDepartments().getFirst());
        movePrimaryDepartmentsAndClearAffiliations(fixture, 9006);
        addProfessorAffiliation(fixture, 9006);
        addLaboratoryAffiliation(fixture, 9006);
        assertUpgradeLeavesDataUnchanged();
    }

    @Test
    void sharedUnrelatedDepartmentIsNotUpdated() {
        var fixture = prepareSelectedTarget(LINKS.getFirst());
        long unrelated = department("자연과학대학", "화학과");
        movePrimaryDepartmentsAndClearAffiliations(fixture, unrelated);
        addProfessorAffiliation(fixture, unrelated);
        addLaboratoryAffiliation(fixture, unrelated);
        assertUpgradeLeavesDataUnchanged();
    }

    @Test
    void differentAffiliationsWithoutCommonAllowedDepartmentAreNotUpdated() {
        Link link = LINKS.stream().filter(candidate -> candidate.allowedDepartments().size() > 1)
            .findFirst().orElse(LINKS.getFirst());
        var fixture = prepareSelectedTarget(link);
        movePrimaryDepartmentsAndClearAffiliations(fixture, department("자연과학대학", "화학과"));
        addProfessorAffiliation(fixture, department("공과대학", link.allowedDepartments().get(0)));
        String differentDepartment = link.allowedDepartments().size() > 1
            ? link.allowedDepartments().get(1)
            : List.of("우주항공공학전공", "지능형드론융합전공", "항공시스템공학과").stream()
                .filter(name -> !link.allowedDepartments().contains(name)).findFirst().orElseThrow();
        addLaboratoryAffiliation(fixture, department("공과대학", differentDepartment));
        assertUpgradeLeavesDataUnchanged();
    }

    @Test
    void professorAffiliationAloneIsNotEnough() {
        var fixture = prepareSelectedTarget(LINKS.getFirst());
        movePrimaryDepartmentsAndClearAffiliations(fixture, department("자연과학대학", "화학과"));
        addProfessorAffiliation(fixture, department("공과대학", fixture.link().allowedDepartments().getFirst()));
        assertUpgradeLeavesDataUnchanged();
    }

    @Test
    void laboratoryAffiliationAloneIsNotEnough() {
        var fixture = prepareSelectedTarget(LINKS.getFirst());
        movePrimaryDepartmentsAndClearAffiliations(fixture, department("자연과학대학", "화학과"));
        addLaboratoryAffiliation(fixture, department("공과대학", fixture.link().allowedDepartments().getFirst()));
        assertUpgradeLeavesDataUnchanged();
    }

    @Test
    void sharedLinkedDepartmentWorksWhenBothPrimaryDepartmentsAreUnrelated() {
        var fixture = prepareSelectedTarget(LINKS.getFirst());
        movePrimaryDepartmentsAndClearAffiliations(fixture, department("자연과학대학", "화학과"));
        long allowed = department("공과대학", fixture.link().allowedDepartments().getFirst());
        addProfessorAffiliation(fixture, allowed);
        addLaboratoryAffiliation(fixture, allowed);
        assertUpgradeChangesOnlySelectedLink(fixture);
    }

    @Test
    void sharedPrimaryDepartmentWorksWithoutAffiliationRows() {
        var fixture = prepareSelectedTarget(LINKS.getFirst());
        movePrimaryDepartmentsAndClearAffiliations(fixture,
            department("공과대학", fixture.link().allowedDepartments().getFirst()));
        assertUpgradeChangesOnlySelectedLink(fixture);
    }

    private Fixture prepareSelectedTarget(Link selected) {
        flyway("50").migrate();
        for (Link link : LINKS) {
            var fixture = fixture(link);
            jdbc.update("""
                UPDATE laboratory SET website_url=?,website_url_source='CRAWLED',
                    updated_at='2001-01-01 00:00:00' WHERE id=?
                """, "https://example.com/existing-lab-" + fixture.laboratoryId(), fixture.laboratoryId());
        }
        var fixture = fixture(selected);
        jdbc.update("UPDATE laboratory SET website_url=NULL,website_url_source=NULL WHERE id=?", fixture.laboratoryId());
        return fixture;
    }

    private Fixture fixture(Link link) {
        var professor = jdbc.queryForMap("SELECT id,name FROM professor WHERE email=?", link.email());
        assertThat(professor).containsEntry("name", link.professorName());
        long professorId = ((Number) professor.get("id")).longValue();
        var laboratory = jdbc.queryForMap("SELECT id,name FROM laboratory WHERE professor_id=? AND deleted_at IS NULL", professorId);
        assertThat(laboratory).containsEntry("name", link.labName());
        return new Fixture(link, professorId, ((Number) laboratory.get("id")).longValue());
    }

    private void addPreservedUserData(Fixture fixture, long id) {
        jdbc.update("INSERT INTO research_field (id,name) VALUES (?,?)", id, "항공 링크 보정 전 연구 분야 " + id);
        jdbc.update("INSERT INTO laboratory_research_field (laboratory_id,research_field_id) VALUES (?,?)", fixture.laboratoryId(), id);
        jdbc.update("INSERT INTO app_user (id,provider,provider_user_id) VALUES (?,'SEJONG',?)", id, "v51-link-test-user-" + id);
        jdbc.update("INSERT INTO bookmark (user_id,laboratory_id) VALUES (?,?)", id, fixture.laboratoryId());
        jdbc.update("""
            INSERT INTO laboratory_review (id,laboratory_id,author_id,category,research_intensity,
                compensation,atmosphere,content,participation_year,participation_term)
            VALUES (?,?,?,'RESEARCH_ENVIRONMENT','MEDIUM','NONE','NORMAL',
                '링크 보정 이후에도 유지해야 하는 후기',2026,'FIRST_SEMESTER')
            """, id, fixture.laboratoryId(), id);
        jdbc.update("INSERT INTO laboratory_review_tag (review_id,tag) VALUES (?,'FREE_ATMOSPHERE')", id);
    }

    private void movePrimaryDepartmentsAndClearAffiliations(Fixture fixture, long departmentId) {
        jdbc.update("UPDATE professor SET department_id=? WHERE id=?", departmentId, fixture.professorId());
        jdbc.update("UPDATE laboratory SET department_id=? WHERE id=?", departmentId, fixture.laboratoryId());
        jdbc.update("DELETE FROM professor_department WHERE professor_id=?", fixture.professorId());
        jdbc.update("DELETE FROM laboratory_department WHERE laboratory_id=?", fixture.laboratoryId());
    }

    private void addProfessorAffiliation(Fixture fixture, long departmentId) {
        jdbc.update("INSERT INTO professor_department (professor_id,department_id) VALUES (?,?)", fixture.professorId(), departmentId);
    }

    private void addLaboratoryAffiliation(Fixture fixture, long departmentId) {
        jdbc.update("INSERT INTO laboratory_department (laboratory_id,department_id) VALUES (?,?)", fixture.laboratoryId(), departmentId);
    }

    private void assertUpgradeLeavesDataUnchanged() {
        var before = snapshotData();
        assertThat(flyway("51").migrate().migrationsExecuted).isOne();
        assertThat(snapshotData()).isEqualTo(before);
    }

    private void assertUpgradeChangesOnlySelectedLink(Fixture fixture) {
        var expected = snapshotData();
        assertThat(flyway("51").migrate().migrationsExecuted).isOne();
        expectOnlyLinkChange(expected, fixture);
        assertThat(snapshotData()).isEqualTo(expected);
    }

    private void expectOnlyLinkChange(Map<String, List<Map<String, Object>>> expected, Fixture fixture) {
        var updated = jdbc.queryForMap("SELECT * FROM laboratory WHERE id=?", fixture.laboratoryId());
        assertThat(updated).containsEntry("website_url", fixture.link().newUrl())
            .containsEntry("website_url_source", "MANUAL");
        assertThat(jdbc.queryForObject("SELECT updated_at FROM laboratory WHERE id=?", Timestamp.class, fixture.laboratoryId()))
            .isAfter(Timestamp.valueOf("2001-01-01 00:00:00"));
        var expectedLaboratory = expected.get("laboratory").stream()
            .filter(row -> ((Number) row.get("id")).longValue() == fixture.laboratoryId()).findFirst().orElseThrow();
        expectedLaboratory.put("website_url", fixture.link().newUrl());
        expectedLaboratory.put("website_url_source", "MANUAL");
        expectedLaboratory.put("updated_at", updated.get("updated_at"));
    }

    private Map<String, List<Map<String, Object>>> snapshotData() {
        var snapshot = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : List.of("college", "department", "professor", "laboratory", "app_user",
            "research_field", "laboratory_review")) {
            snapshot.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        }
        snapshot.put("professor_department", jdbc.queryForList("SELECT * FROM professor_department ORDER BY professor_id,department_id"));
        snapshot.put("laboratory_department", jdbc.queryForList("SELECT * FROM laboratory_department ORDER BY laboratory_id,department_id"));
        snapshot.put("laboratory_research_field", jdbc.queryForList("SELECT * FROM laboratory_research_field ORDER BY laboratory_id,research_field_id"));
        snapshot.put("bookmark", jdbc.queryForList("SELECT * FROM bookmark ORDER BY user_id,laboratory_id"));
        snapshot.put("laboratory_review_tag", jdbc.queryForList("SELECT * FROM laboratory_review_tag ORDER BY review_id,tag"));
        return snapshot;
    }

    private long department(String college, String name) {
        return jdbc.queryForObject("SELECT d.id FROM department d JOIN college c ON c.id=d.college_id WHERE c.name=? AND d.name=?",
            Long.class, college, name);
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource()).locations("classpath:db/migration")
            .cleanDisabled(false).target(target).load();
    }

    private void validateHibernate() {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource());
        factory.setPackagesToScan("com.sebu.backend");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate",
            "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        try {
            factory.afterPropertiesSet();
            assertThat(factory.getObject()).isNotNull();
            assertThat(factory.getObject().isOpen()).isTrue();
        } finally {
            factory.destroy();
        }
    }

    private static List<Link> readLinks() {
        var resource = new ClassPathResource("aerospace-laboratory-links.csv");
        try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            assertThat(header).isNotNull();
            assertThat(header.replace("\uFEFF", "").split(",", -1))
                .containsExactly("professorName", "email", "labName", "newUrl", "allowedDepartments");
            var links = new ArrayList<Link>();
            for (String line; (line = reader.readLine()) != null;) {
                if (line.isBlank()) continue;
                // This reviewed fixture contains no commas, quotes, or newlines within a field.
                assertThat(line).doesNotContain("\"");
                var values = List.of(line.split(",", -1));
                assertThat(values).hasSize(5).allSatisfy(value -> assertThat(value).isNotBlank());
                var departments = List.of(values.get(4).split("\\|", -1));
                assertThat(departments).doesNotHaveDuplicates().allSatisfy(value -> assertThat(value).isNotBlank());
                links.add(new Link(values.get(0), values.get(1), values.get(2), values.get(3), departments));
            }
            assertThat(links).isNotEmpty();
            assertThat(links.stream().map(Link::email).toList()).doesNotHaveDuplicates();
            return List.copyOf(links);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read verified aerospace laboratory links", exception);
        }
    }

    private record Link(String professorName, String email, String labName, String newUrl, List<String> allowedDepartments) {}

    private record Fixture(Link link, long professorId, long laboratoryId) {}
}
