package com.sebu.backend.researchfield.manualsplit.service;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.researchfield.candidate.domain.LaboratoryResearchFieldCandidate;
import com.sebu.backend.researchfield.candidate.domain.ResearchFieldCandidateDraft;
import com.sebu.backend.researchfield.candidate.domain.ResearchFieldCandidateReviewStatus;
import com.sebu.backend.researchfield.candidate.domain.ResearchFieldExtractionMethod;
import com.sebu.backend.researchfield.candidate.repository.LaboratoryResearchFieldCandidateRepository;
import com.sebu.backend.researchfield.manualsplit.dto.ManualSplitCsvRow;
import com.sebu.backend.researchfield.manualsplit.dto.ManualSplitImportResult;
import com.sebu.backend.researchfield.manualsplit.exception.ManualSplitImportException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ManualSplitImportServiceIntegrationTest {
    private static final LocalDateTime EXTRACTED_AT = LocalDateTime.of(
        2026,
        8,
        28,
        10,
        0
    );

    @Autowired
    ManualSplitImportService importService;

    @Autowired
    LaboratoryResearchFieldCandidateRepository candidateRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;

    @ParameterizedTest
    @EnumSource(value = ResearchFieldExtractionMethod.class, names = {"WHOLE_TEXT", "DELIMITED", "LONG_TEXT"})
    void importsManualSplitsRejectsTheSourceAndReplaysIdempotently(ResearchFieldExtractionMethod method) {
        FixtureIds fixture = createFixture(method);
        try {
            List<ManualSplitCsvRow> rows = List.of(
                row(fixture, 1, "자율주행 인공지능", 2),
                row(fixture, 2, "영상·라이다 환경 인식", 3)
            );

            ManualSplitImportResult first = importService.importRows(
                rows,
                "integration-reviewer"
            );
            ManualSplitImportResult replay = importService.importRows(
                rows,
                "integration-reviewer"
            );
            List<CandidateSnapshot> candidates = snapshots(fixture.laboratoryId());

            assertThat(first).isEqualTo(new ManualSplitImportResult(1, 2, 2, 0, 1));
            assertThat(replay).isEqualTo(new ManualSplitImportResult(1, 2, 0, 2, 0));
            assertThat(candidates).hasSize(3);
            assertThat(candidates.getFirst().id()).isEqualTo(fixture.sourceCandidateId());
            assertThat(candidates.getFirst().reviewStatus()).isEqualTo(
                ResearchFieldCandidateReviewStatus.REJECTED
            );
            assertThat(candidates.subList(1, 3))
                .extracting(CandidateSnapshot::candidateName)
                .containsExactly(
                    "자율주행 인공지능",
                    "영상·라이다 환경 인식"
                );
            assertThat(candidates.subList(1, 3)).allSatisfy(candidate -> {
                assertThat(candidate.extractionMethod()).isEqualTo(
                    ResearchFieldExtractionMethod.MANUAL_SPLIT
                );
                assertThat(candidate.reviewStatus()).isEqualTo(
                    ResearchFieldCandidateReviewStatus.PENDING
                );
                assertThat(candidate.splitFromCandidateId()).isEqualTo(
                    fixture.sourceCandidateId()
                );
            });
        } finally {
            deleteFixture(fixture);
        }
    }

    @Test
    void rollsBackEverySourceWhenAnyCsvRowIsInvalid() {
        FixtureIds fixture = createFixture();
        try {
            List<ManualSplitCsvRow> rows = List.of(
                row(fixture, 1, "자율주행 인공지능", 2),
                new ManualSplitCsvRow(
                    Long.MAX_VALUE,
                    fixture.laboratoryId(),
                    2,
                    "컴퓨터비전",
                    3
                )
            );

            assertThatThrownBy(() -> importService.importRows(
                rows,
                "integration-reviewer"
            ))
                .isInstanceOf(ManualSplitImportException.class)
                .hasMessageContaining("MANUAL_SPLIT_SOURCE_NOT_FOUND");

            List<CandidateSnapshot> candidates = snapshots(fixture.laboratoryId());
            assertThat(candidates).singleElement().satisfies(candidate -> {
                assertThat(candidate.id()).isEqualTo(fixture.sourceCandidateId());
                assertThat(candidate.reviewStatus()).isEqualTo(
                    ResearchFieldCandidateReviewStatus.PENDING
                );
            });
        } finally {
            deleteFixture(fixture);
        }
    }

    private ManualSplitCsvRow row(
        FixtureIds fixture,
        int sourceOrder,
        String candidateName,
        int lineNumber
    ) {
        return new ManualSplitCsvRow(
            fixture.sourceCandidateId(),
            fixture.laboratoryId(),
            sourceOrder,
            candidateName,
            lineNumber
        );
    }

    private FixtureIds createFixture() {
        return createFixture(ResearchFieldExtractionMethod.LONG_TEXT);
    }

    private FixtureIds createFixture(ResearchFieldExtractionMethod method) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            College college = new College("수동 분리 테스트 대학 " + suffix);
            Department department = new Department(college, "수동 분리 테스트 학과");
            Professor professor = new Professor(
                department,
                "수동 분리 테스트 교수",
                "manual-split-" + suffix + "@example.com"
            );
            Laboratory laboratory = new Laboratory(
                professor,
                department,
                "수동 분리 테스트 연구실 " + suffix,
                null,
                "자율주행자동차와 드론의 환경 인식 및 제어를 연구합니다.",
                RecruitmentStatus.UNKNOWN
            );
            entityManager.persist(college);
            entityManager.persist(department);
            entityManager.persist(professor);
            entityManager.persist(laboratory);
            LaboratoryResearchFieldCandidate source =
                new LaboratoryResearchFieldCandidate(
                    laboratory,
                    new ResearchFieldCandidateDraft(
                        "a".repeat(64),
                        laboratory.getDescription(),
                        method == ResearchFieldExtractionMethod.LONG_TEXT ? null : laboratory.getDescription(),
                        method,
                        0
                    ),
                    "b".repeat(64),
                    "sejong-v1",
                    EXTRACTED_AT
                );
            entityManager.persist(source);
            entityManager.flush();
            return new FixtureIds(
                college.getId(),
                department.getId(),
                professor.getId(),
                laboratory.getId(),
                source.getId()
            );
        });
    }

    private List<CandidateSnapshot> snapshots(long laboratoryId) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> candidateRepository.findAll().stream()
            .filter(candidate -> candidate.getLaboratory().getId().equals(laboratoryId))
            .sorted(Comparator.comparing(LaboratoryResearchFieldCandidate::getId))
            .map(candidate -> new CandidateSnapshot(
                candidate.getId(),
                candidate.getCandidateName(),
                candidate.getExtractionMethod(),
                candidate.getReviewStatus(),
                candidate.getSplitFromCandidate() == null
                    ? null
                    : candidate.getSplitFromCandidate().getId()
            ))
            .toList());
    }

    private void deleteFixture(FixtureIds fixture) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            entityManager.createNativeQuery("""
                DELETE FROM laboratory_research_field_candidate
                WHERE laboratory_id = :laboratoryId
                  AND split_from_candidate_id IS NOT NULL
                """)
                .setParameter("laboratoryId", fixture.laboratoryId())
                .executeUpdate();
            deleteById(
                "laboratory_research_field_candidate",
                "laboratory_id",
                fixture.laboratoryId()
            );
            deleteById("laboratory", "id", fixture.laboratoryId());
            deleteById("professor", "id", fixture.professorId());
            deleteById("department", "id", fixture.departmentId());
            deleteById("college", "id", fixture.collegeId());
        });
    }

    private void deleteById(String table, String column, long id) {
        entityManager.createNativeQuery(
            "DELETE FROM " + table + " WHERE " + column + " = :id"
        )
            .setParameter("id", id)
            .executeUpdate();
    }

    private record FixtureIds(
        long collegeId,
        long departmentId,
        long professorId,
        long laboratoryId,
        long sourceCandidateId
    ) { }

    private record CandidateSnapshot(
        long id,
        String candidateName,
        ResearchFieldExtractionMethod extractionMethod,
        ResearchFieldCandidateReviewStatus reviewStatus,
        Long splitFromCandidateId
    ) { }
}
