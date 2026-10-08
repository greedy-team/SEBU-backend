package com.sebu.backend.researchfield.extraction.service;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.researchfield.candidate.domain.LaboratoryResearchFieldCandidate;
import com.sebu.backend.researchfield.candidate.domain.ResearchFieldCandidateDraft;
import com.sebu.backend.researchfield.candidate.domain.ResearchFieldExtractionMethod;
import com.sebu.backend.researchfield.extraction.dto.ResearchFieldCandidateReconciliation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchFieldCandidateReconcilerTest {
    private static final String DESCRIPTION_HASH = "d".repeat(64);
    private static final LocalDateTime EXTRACTED_AT = LocalDateTime.of(2026, 8, 27, 10, 0);

    private final ResearchFieldTextHasher hasher = new ResearchFieldTextHasher();
    private ResearchFieldCandidateReconciler reconciler;
    private Laboratory laboratory;

    @BeforeEach
    void setUp() {
        reconciler = new ResearchFieldCandidateReconciler();
        College college = new College("테스트 대학");
        Department department = new Department(college, "테스트 학과");
        Professor professor = new Professor(department, "테스트 교수", null);
        laboratory = new Laboratory(
            professor,
            department,
            "테스트 연구실",
            null,
            "인공지능, 로보틱스",
            RecruitmentStatus.UNKNOWN
        );
    }

    @Test
    void createsCandidatesOnceAndLeavesAnIdenticalRerunUnchanged() {
        List<ResearchFieldCandidateDraft> drafts = List.of(
            draft("인공지능", 0),
            draft("로보틱스", 1)
        );
        ResearchFieldCandidateReconciliation first = reconcile(drafts, List.of());
        List<LaboratoryResearchFieldCandidate> existing = new ArrayList<>(
            first.createdCandidates()
        );
        ResearchFieldCandidateReconciliation second = reconcile(drafts, existing);

        assertThat(first.createdCount()).isEqualTo(2);
        assertThat(second.createdCount()).isZero();
        assertThat(second.refreshedCount()).isZero();
        assertThat(second.staleCount()).isZero();
        assertThat(second.unchangedCount()).isEqualTo(2);
    }

    @Test
    void createsNewFieldsAndMarksDisappearedFieldsAsStale() {
        ResearchFieldCandidateReconciliation first = reconcile(
            List.of(draft("인공지능", 0), draft("로보틱스", 1)),
            List.of()
        );

        ResearchFieldCandidateReconciliation second = reconcile(
            List.of(draft("인공지능", 0), draft("컴퓨터 비전", 1)),
            new ArrayList<>(first.createdCandidates())
        );

        assertThat(second.createdCount()).isOne();
        assertThat(second.staleCount()).isOne();
        assertThat(first.createdCandidates())
            .filteredOn(candidate -> candidate.getCandidateName().equals("로보틱스"))
            .singleElement()
            .extracting(LaboratoryResearchFieldCandidate::isStale)
            .isEqualTo(true);
    }

    @Test
    void preservesManualSplitsWhileTheirLongTextSourceIsCurrent() {
        ResearchFieldCandidateDraft sourceDraft = longTextDraft();
        LaboratoryResearchFieldCandidate source = candidate(sourceDraft);
        LaboratoryResearchFieldCandidate split =
            LaboratoryResearchFieldCandidate.manualSplit(
                source,
                new ResearchFieldCandidateDraft(
                    hasher.hashFieldIdentity("자율주행 인공지능"),
                    "자율주행 인공지능",
                    "자율주행 인공지능",
                    ResearchFieldExtractionMethod.MANUAL_SPLIT,
                    1
                ),
                "manual-split-csv-v1",
                EXTRACTED_AT
            );
        source.rejectAfterManualSplit("reviewer", null, EXTRACTED_AT);

        ResearchFieldCandidateReconciliation result = reconcile(
            List.of(sourceDraft),
            List.of(source, split)
        );

        assertThat(result.staleCount()).isZero();
        assertThat(source.isStale()).isFalse();
        assertThat(split.isStale()).isFalse();
    }

    @Test
    void marksManualSplitsStaleWhenTheirLongTextSourceDisappears() {
        LaboratoryResearchFieldCandidate source = candidate(longTextDraft());
        LaboratoryResearchFieldCandidate split =
            LaboratoryResearchFieldCandidate.manualSplit(
                source,
                new ResearchFieldCandidateDraft(
                    hasher.hashFieldIdentity("컴퓨터비전"),
                    "컴퓨터비전",
                    "컴퓨터비전",
                    ResearchFieldExtractionMethod.MANUAL_SPLIT,
                    1
                ),
                "manual-split-csv-v1",
                EXTRACTED_AT
            );
        source.rejectAfterManualSplit("reviewer", null, EXTRACTED_AT);

        ResearchFieldCandidateReconciliation result = reconcile(
            List.of(),
            List.of(source, split)
        );

        assertThat(result.staleCount()).isEqualTo(2);
        assertThat(source.isStale()).isTrue();
        assertThat(split.isStale()).isTrue();
    }

    private ResearchFieldCandidateReconciliation reconcile(
        List<ResearchFieldCandidateDraft> drafts,
        List<LaboratoryResearchFieldCandidate> existing
    ) {
        return reconciler.reconcile(
            laboratory,
            drafts,
            existing,
            DESCRIPTION_HASH,
            ResearchFieldTextExtractor.RULE_VERSION,
            EXTRACTED_AT
        );
    }

    private ResearchFieldCandidateDraft draft(String name, int order) {
        return new ResearchFieldCandidateDraft(
            hasher.hashFieldIdentity(name),
            name,
            name,
            ResearchFieldExtractionMethod.DELIMITED,
            order
        );
    }

    private ResearchFieldCandidateDraft longTextDraft() {
        String rawText = "자율주행자동차와 드론의 환경 인식 및 제어를 연구합니다.";
        return new ResearchFieldCandidateDraft(
            hasher.hashFieldIdentity(rawText),
            rawText,
            null,
            ResearchFieldExtractionMethod.LONG_TEXT,
            0
        );
    }

    private LaboratoryResearchFieldCandidate candidate(
        ResearchFieldCandidateDraft draft
    ) {
        return new LaboratoryResearchFieldCandidate(
            laboratory,
            draft,
            DESCRIPTION_HASH,
            ResearchFieldTextExtractor.RULE_VERSION,
            EXTRACTED_AT
        );
    }
}
