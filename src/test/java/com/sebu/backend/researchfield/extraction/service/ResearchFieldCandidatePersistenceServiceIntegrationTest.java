package com.sebu.backend.researchfield.extraction.service;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.researchfield.candidate.domain.LaboratoryResearchFieldCandidate;
import com.sebu.backend.researchfield.candidate.repository.LaboratoryResearchFieldCandidateRepository;
import com.sebu.backend.researchfield.extraction.dto.ResearchFieldExtractionResult;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ResearchFieldCandidatePersistenceServiceIntegrationTest {
    @Autowired
    ResearchFieldCandidatePersistenceService persistenceService;

    @Autowired
    LaboratoryResearchFieldCandidateRepository candidateRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void extractsFinalLaboratoryDescriptionIdempotentlyIntoPendingCandidates() {
        FixtureIds fixture = createFixture();
        try {
            ResearchFieldExtractionResult first = persistenceService.extract(
                fixture.laboratoryId()
            );
            ResearchFieldExtractionResult second = persistenceService.extract(
                fixture.laboratoryId()
            );
            List<LaboratoryResearchFieldCandidate> candidates = candidateRepository.findAll()
                .stream()
                .filter(candidate -> candidate.getLaboratory().getId()
                    .equals(fixture.laboratoryId()))
                .toList();

            assertThat(first.createdCount()).isEqualTo(3);
            assertThat(second.createdCount()).isZero();
            assertThat(second.unchangedCount()).isEqualTo(3);
            assertThat(candidates)
                .extracting(LaboratoryResearchFieldCandidate::getCandidateName)
                .containsExactlyInAnyOrder(
                    "기계학습",
                    "Video Coding (HEVC, VVC)",
                    "5G/6G"
                );
            assertThat(candidates).allSatisfy(candidate -> {
                assertThat(candidate.isStale()).isFalse();
                assertThat(candidate.getReviewedAt()).isNull();
            });
        } finally {
            deleteFixture(fixture);
        }
    }

    private FixtureIds createFixture() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            College college = new College("추출 통합 테스트 대학 " + suffix);
            Department department = new Department(college, "추출 통합 테스트 학과");
            Professor professor = new Professor(
                department,
                "추출 통합 테스트 교수",
                "extraction-" + suffix + "@example.com"
            );
            Laboratory laboratory = new Laboratory(
                professor,
                department,
                "추출 통합 테스트 연구실 " + suffix,
                null,
                "기계학습, Video Coding (HEVC, VVC), 5G/6G",
                RecruitmentStatus.UNKNOWN
            );
            entityManager.persist(college);
            entityManager.persist(department);
            entityManager.persist(professor);
            entityManager.persist(laboratory);
            entityManager.flush();
            return new FixtureIds(
                college.getId(),
                department.getId(),
                professor.getId(),
                laboratory.getId()
            );
        });
    }

    private void deleteFixture(FixtureIds fixture) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            deleteById("laboratory_research_field_candidate", "laboratory_id", fixture.laboratoryId());
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
        long laboratoryId
    ) {
    }
}
