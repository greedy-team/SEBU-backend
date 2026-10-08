package com.sebu.backend.researchfield.candidate.repository;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.researchfield.candidate.domain.LaboratoryResearchFieldCandidate;
import com.sebu.backend.researchfield.candidate.domain.ResearchFieldCandidateDraft;
import com.sebu.backend.researchfield.candidate.domain.ResearchFieldCandidateReviewStatus;
import com.sebu.backend.researchfield.candidate.domain.ResearchFieldExtractionMethod;
import com.sebu.backend.researchfield.extraction.runner.ResearchFieldCandidateExtractionRunner;
import com.sebu.backend.researchfield.manualsplit.runner.ManualSplitImportRunner;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ResearchFieldCandidateSchemaIntegrationTest {
    @Autowired
    EntityManager entityManager;

    @Autowired
    LaboratoryResearchFieldCandidateRepository candidateRepository;

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void candidateEntityMatchesTheFlywaySchema() {
        String suffix = UUID.randomUUID().toString();
        College college = new College("연구 분야 후보 대학 " + suffix);
        Department department = new Department(college, "연구 분야 후보 학과");
        Professor professor = new Professor(
            department,
            "연구 분야 후보 교수",
            "field-" + suffix + "@example.com"
        );
        Laboratory laboratory = new Laboratory(
            professor,
            department,
            "연구 분야 후보 연구실 " + suffix,
            null,
            "인공지능, 로보틱스",
            RecruitmentStatus.UNKNOWN
        );
        entityManager.persist(college);
        entityManager.persist(department);
        entityManager.persist(professor);
        entityManager.persist(laboratory);
        LaboratoryResearchFieldCandidate saved = candidateRepository.saveAndFlush(
            new LaboratoryResearchFieldCandidate(
                laboratory,
                new ResearchFieldCandidateDraft(
                    "a".repeat(64),
                    "인공지능",
                    "인공지능",
                    ResearchFieldExtractionMethod.DELIMITED,
                    0
                ),
                "b".repeat(64),
                "sejong-v1",
                LocalDateTime.of(2026, 8, 27, 10, 0)
            )
        );

        entityManager.clear();

        LaboratoryResearchFieldCandidate found = candidateRepository
            .findById(saved.getId())
            .orElseThrow();
        assertThat(found.getLaboratory().getId()).isEqualTo(laboratory.getId());
        assertThat(found.getCandidateName()).isEqualTo("인공지능");
        assertThat(found.getReviewStatus()).isEqualTo(
            ResearchFieldCandidateReviewStatus.PENDING
        );
        assertThat(found.getVersion()).isZero();
        assertThat(found.isStale()).isFalse();
    }

    @Test
    void normalServerProfileDoesNotCreateTheOneTimeExtractionRunner() {
        assertThat(applicationContext.getBeansOfType(
            ResearchFieldCandidateExtractionRunner.class
        )).isEmpty();
        assertThat(applicationContext.getBeansOfType(
            ManualSplitImportRunner.class
        )).isEmpty();
    }
}
