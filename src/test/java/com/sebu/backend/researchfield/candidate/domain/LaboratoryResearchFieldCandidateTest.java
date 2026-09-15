package com.sebu.backend.researchfield.candidate.domain;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.researchfield.domain.ResearchField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LaboratoryResearchFieldCandidateTest {
    private static final String FIELD_KEY = "a".repeat(64);
    private static final String DESCRIPTION_HASH = "b".repeat(64);
    private static final LocalDateTime EXTRACTED_AT = LocalDateTime.of(2026, 8, 27, 10, 0);

    @Test
    void requiresAReviewedNameBeforeApproval() {
        LaboratoryResearchFieldCandidate candidate = candidate(null);

        assertThatThrownBy(() -> candidate.approve(
            "reviewer",
            null,
            EXTRACTED_AT.plusHours(1)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("CANDIDATE_NAME_REQUIRED_FOR_APPROVAL");

        candidate.reviseCandidateName("자율주행");
        candidate.approve("reviewer", "검수 완료", EXTRACTED_AT.plusHours(1));

        assertThat(candidate.isCurrentAndApproved()).isTrue();
        assertThat(candidate.getReviewRevision()).isOne();
        assertThat(candidate.getReviewedBy()).isEqualTo("reviewer");
    }

    @Test
    void staleCandidateCannotBeReviewedAndReappearanceRequiresReviewAgain() {
        LaboratoryResearchFieldCandidate candidate = candidate("인공지능");
        candidate.approve("reviewer", null, EXTRACTED_AT.plusHours(1));
        candidate.markStale();

        assertThatThrownBy(() -> candidate.reject(
            "reviewer",
            null,
            EXTRACTED_AT.plusHours(2)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("STALE_CANDIDATE_NOT_REVIEWABLE");

        boolean changed = candidate.refreshFromExtraction(
            draft("인공지능"),
            "c".repeat(64),
            "sejong-v1",
            EXTRACTED_AT.plusHours(3)
        );

        assertThat(changed).isTrue();
        assertThat(candidate.isStale()).isFalse();
        assertThat(candidate.getReviewStatus()).isEqualTo(
            ResearchFieldCandidateReviewStatus.PENDING
        );
        assertThat(candidate.getReviewedBy()).isNull();
        assertThat(candidate.getReviewRevision()).isOne();
    }

    @Test
    void identicalExtractionDoesNotMutateAnExistingCandidate() {
        LaboratoryResearchFieldCandidate candidate = candidate("인공지능");

        boolean changed = candidate.refreshFromExtraction(
            draft("인공지능"),
            DESCRIPTION_HASH,
            "sejong-v1",
            EXTRACTED_AT.plusHours(1)
        );

        assertThat(changed).isFalse();
        assertThat(candidate.getExtractedAt()).isEqualTo(EXTRACTED_AT);
    }

    @Test
    void refreshesOnlyAnUntouchedAutomaticCandidateName() {
        LaboratoryResearchFieldCandidate automaticCandidate = candidateWithRawAndName("ai", "ai");
        LaboratoryResearchFieldCandidate manuallyRevisedCandidate =
            candidateWithRawAndName("ai", "ai");
        manuallyRevisedCandidate.reviseCandidateName("인공지능");
        ResearchFieldCandidateDraft refreshedDraft = new ResearchFieldCandidateDraft(
            FIELD_KEY,
            "AI",
            "AI",
            ResearchFieldExtractionMethod.WHOLE_TEXT,
            0
        );

        automaticCandidate.refreshFromExtraction(
            refreshedDraft,
            "c".repeat(64),
            "sejong-v1",
            EXTRACTED_AT.plusHours(1)
        );
        manuallyRevisedCandidate.refreshFromExtraction(
            refreshedDraft,
            "c".repeat(64),
            "sejong-v1",
            EXTRACTED_AT.plusHours(1)
        );

        assertThat(automaticCandidate.getCandidateName()).isEqualTo("AI");
        assertThat(manuallyRevisedCandidate.getCandidateName()).isEqualTo("인공지능");
    }

    @Test
    void createsManualSplitCandidatesFromAnUnresolvedLongTextSource() {
        LaboratoryResearchFieldCandidate source = candidate(null);
        ResearchFieldCandidateDraft splitDraft = new ResearchFieldCandidateDraft(
            "c".repeat(64),
            "자율주행 인공지능",
            "자율주행 인공지능",
            ResearchFieldExtractionMethod.MANUAL_SPLIT,
            1
        );

        LaboratoryResearchFieldCandidate split =
            LaboratoryResearchFieldCandidate.manualSplit(
                source,
                splitDraft,
                "manual-split-csv-v1",
                EXTRACTED_AT.plusHours(1)
            );
        source.rejectAfterManualSplit(
            "reviewer",
            "수동 분리 완료",
            EXTRACTED_AT.plusHours(1)
        );

        assertThat(split.getSplitFromCandidate()).isSameAs(source);
        assertThat(split.getCandidateName()).isEqualTo("자율주행 인공지능");
        assertThat(split.getExtractionMethod()).isEqualTo(
            ResearchFieldExtractionMethod.MANUAL_SPLIT
        );
        assertThat(split.getReviewStatus()).isEqualTo(
            ResearchFieldCandidateReviewStatus.PENDING
        );
        assertThat(source.getReviewStatus()).isEqualTo(
            ResearchFieldCandidateReviewStatus.REJECTED
        );
    }

    @Test
    void manualSplitBecomesStaleWhenItsSourceBecomesStale() {
        LaboratoryResearchFieldCandidate source = candidate(null);
        LaboratoryResearchFieldCandidate split =
            LaboratoryResearchFieldCandidate.manualSplit(
                source,
                new ResearchFieldCandidateDraft(
                    "c".repeat(64),
                    "컴퓨터비전",
                    "컴퓨터비전",
                    ResearchFieldExtractionMethod.MANUAL_SPLIT,
                    1
                ),
                "manual-split-csv-v1",
                EXTRACTED_AT.plusHours(1)
            );

        source.markStale();

        assertThat(split.shouldBecomeStaleFromSplitSource()).isTrue();
    }

    @Test
    void approvedCurrentCandidateNeedsPromotion() {
        LaboratoryResearchFieldCandidate candidate = candidate("인공지능");

        candidate.approve("reviewer", "검수 완료", EXTRACTED_AT.plusHours(1));

        assertThat(candidate.needsPromotion()).isTrue();
        assertThat(candidate.hasBeenPromoted()).isFalse();
        assertThat(candidate.hasConsistentPromotionState()).isTrue();
    }

    @Test
    void recordsResearchFieldAndReviewSnapshotWhenPromoted() {
        LaboratoryResearchFieldCandidate candidate = candidate("인공지능");
        LocalDateTime reviewedAt = EXTRACTED_AT.plusHours(1);
        LocalDateTime promotedAt = EXTRACTED_AT.plusHours(2);
        ResearchField researchField = new ResearchField("인공지능");
        candidate.approve("reviewer", "검수 완료", reviewedAt);

        candidate.recordPromotion(researchField, promotedAt);

        assertThat(candidate.getPromotedResearchField()).isSameAs(researchField);
        assertThat(candidate.getPromotedAt()).isEqualTo(promotedAt);
        assertThat(candidate.getPromotedReviewedAt()).isEqualTo(reviewedAt);
        assertThat(candidate.getPromotedReviewRevision()).isOne();
        assertThat(candidate.needsPromotion()).isFalse();
        assertThat(candidate.hasBeenPromoted()).isTrue();
        assertThat(candidate.hasConsistentPromotionState()).isTrue();
    }

    @Test
    void candidateMustBeReadyBeforePromotion() {
        LaboratoryResearchFieldCandidate candidate = candidate("인공지능");

        assertThatThrownBy(() -> candidate.recordPromotion(
            new ResearchField("인공지능"),
            EXTRACTED_AT.plusHours(1)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("CANDIDATE_NOT_READY_FOR_PROMOTION");
    }

    @Test
    void promotionRequiresResearchFieldAndPromotionTime() {
        LaboratoryResearchFieldCandidate candidateWithoutField = candidate("인공지능");
        candidateWithoutField.approve(
            "reviewer",
            null,
            EXTRACTED_AT.plusHours(1)
        );

        assertThatThrownBy(() -> candidateWithoutField.recordPromotion(
            null,
            EXTRACTED_AT.plusHours(2)
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("PROMOTED_RESEARCH_FIELD_REQUIRED");

        LaboratoryResearchFieldCandidate candidateWithoutTime = candidate("로보틱스");
        candidateWithoutTime.approve(
            "reviewer",
            null,
            EXTRACTED_AT.plusHours(1)
        );

        assertThatThrownBy(() -> candidateWithoutTime.recordPromotion(
            new ResearchField("로보틱스"),
            null
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("PROMOTED_AT_REQUIRED");
        assertThat(candidateWithoutTime.hasBeenPromoted()).isFalse();
        assertThat(candidateWithoutTime.hasConsistentPromotionState()).isTrue();
    }

    @Test
    void rePromotionCanOnlyRefreshTheSameResearchField() {
        LaboratoryResearchFieldCandidate candidate = candidate("인공지능");
        ResearchField originalResearchField = new ResearchField("인공지능");
        candidate.approve("reviewer", null, EXTRACTED_AT.plusHours(1));
        candidate.recordPromotion(originalResearchField, EXTRACTED_AT.plusHours(2));
        candidate.markStale();
        candidate.refreshFromExtraction(
            draft("인공지능"),
            "c".repeat(64),
            "sejong-v2",
            EXTRACTED_AT.plusHours(3)
        );
        candidate.approve("reviewer", "재검수 완료", EXTRACTED_AT.plusHours(4));

        assertThat(candidate.needsPromotion()).isTrue();
        assertThatThrownBy(() -> candidate.recordPromotion(
            new ResearchField("다른 연구 분야"),
            EXTRACTED_AT.plusHours(5)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PROMOTED_ENTITY_CANNOT_BE_REPLACED");

        candidate.recordPromotion(originalResearchField, EXTRACTED_AT.plusHours(5));

        assertThat(candidate.getPromotedResearchField()).isSameAs(originalResearchField);
        assertThat(candidate.getPromotedReviewRevision()).isEqualTo(2L);
        assertThat(candidate.getPromotedReviewedAt()).isEqualTo(
            EXTRACTED_AT.plusHours(4)
        );
        assertThat(candidate.needsPromotion()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ResearchFieldExtractionMethod.class, names = {"WHOLE_TEXT", "DELIMITED", "LONG_TEXT"})
    void allowsExplicitManualReviewOfCompoundAutomaticCandidates(ResearchFieldExtractionMethod method) {
        LaboratoryResearchFieldCandidate source = new LaboratoryResearchFieldCandidate(
            laboratory(),
            new ResearchFieldCandidateDraft(FIELD_KEY, "반도체 태양전지", "반도체 태양전지", method, 0),
            DESCRIPTION_HASH, "sejong-v2", EXTRACTED_AT
        );

        LaboratoryResearchFieldCandidate split = split(source);
        source.rejectAfterManualSplit("reviewer", "복합 분야 분리", EXTRACTED_AT.plusHours(1));

        assertThat(split.getSplitFromCandidate()).isSameAs(source);
        assertThat(split.getReviewStatus()).isEqualTo(ResearchFieldCandidateReviewStatus.PENDING);
        assertThat(source.getRawFieldText()).isEqualTo("반도체 태양전지");
        assertThat(source.getCandidateName()).isEqualTo("반도체 태양전지");
        assertThat(source.getExtractionMethod()).isEqualTo(method);
        assertThat(source.getReviewStatus()).isEqualTo(ResearchFieldCandidateReviewStatus.REJECTED);
    }

    @Test
    void cannotSplitApprovedRejectedStaleOrManuallySplitCandidates() {
        LaboratoryResearchFieldCandidate approved = candidate("복합 분야");
        approved.approve("reviewer", null, EXTRACTED_AT);
        LaboratoryResearchFieldCandidate rejected = candidate("복합 분야");
        rejected.reject("reviewer", null, EXTRACTED_AT);
        LaboratoryResearchFieldCandidate stale = candidate("복합 분야");
        stale.markStale();
        LaboratoryResearchFieldCandidate child = split(candidate("복합 분야"));

        assertThatThrownBy(() -> split(approved)).hasMessage("CANDIDATE_ALREADY_REVIEWED");
        assertThatThrownBy(() -> split(rejected)).hasMessage("CANDIDATE_ALREADY_REVIEWED");
        assertThatThrownBy(() -> split(stale)).hasMessage("STALE_CANDIDATE_NOT_REVIEWABLE");
        assertThatThrownBy(() -> split(child)).hasMessage("AUTOMATIC_SPLIT_SOURCE_REQUIRED");
        assertThatThrownBy(() -> child.rejectAfterManualSplit("reviewer", null, EXTRACTED_AT))
            .hasMessage("AUTOMATIC_SPLIT_SOURCE_REQUIRED");
    }

    @Test
    void cannotSplitAPreviouslyPromotedCandidateThatReturnedToPending() {
        LaboratoryResearchFieldCandidate source = candidate("복합 분야");
        source.approve("reviewer", null, EXTRACTED_AT);
        source.recordPromotion(new ResearchField("복합 분야"), EXTRACTED_AT.plusMinutes(1));
        source.markStale();
        source.refreshFromExtraction(draft("복합 분야"), "c".repeat(64), "sejong-v2", EXTRACTED_AT.plusHours(1));

        assertThatThrownBy(() -> split(source)).hasMessage("PROMOTED_SOURCE_CANNOT_BE_SPLIT");
        assertThatThrownBy(() -> source.rejectAfterManualSplit("reviewer", null, EXTRACTED_AT.plusHours(2)))
            .hasMessage("PROMOTED_SOURCE_CANNOT_BE_SPLIT");
        assertThat(source.getReviewStatus()).isEqualTo(ResearchFieldCandidateReviewStatus.PENDING);
        assertThat(source.getPromotedResearchField().getName()).isEqualTo("복합 분야");
    }

    private LaboratoryResearchFieldCandidate split(LaboratoryResearchFieldCandidate source) {
        return LaboratoryResearchFieldCandidate.manualSplit(
            source,
            new ResearchFieldCandidateDraft("c".repeat(64), "반도체", "반도체", ResearchFieldExtractionMethod.MANUAL_SPLIT, 1),
            "manual-split-csv-v1", EXTRACTED_AT.plusHours(1)
        );
    }

    private LaboratoryResearchFieldCandidate candidate(String candidateName) {
        return new LaboratoryResearchFieldCandidate(
            laboratory(),
            draft(candidateName),
            DESCRIPTION_HASH,
            "sejong-v1",
            EXTRACTED_AT
        );
    }

    private LaboratoryResearchFieldCandidate candidateWithRawAndName(
        String rawFieldText,
        String candidateName
    ) {
        return new LaboratoryResearchFieldCandidate(
            laboratory(),
            new ResearchFieldCandidateDraft(
                FIELD_KEY,
                rawFieldText,
                candidateName,
                ResearchFieldExtractionMethod.WHOLE_TEXT,
                0
            ),
            DESCRIPTION_HASH,
            "sejong-v1",
            EXTRACTED_AT
        );
    }

    private ResearchFieldCandidateDraft draft(String candidateName) {
        return new ResearchFieldCandidateDraft(
            FIELD_KEY,
            candidateName == null ? "긴 연구실 소개 문장" : candidateName,
            candidateName,
            candidateName == null
                ? ResearchFieldExtractionMethod.LONG_TEXT
                : ResearchFieldExtractionMethod.WHOLE_TEXT,
            0
        );
    }

    private Laboratory laboratory() {
        College college = new College("테스트 대학");
        Department department = new Department(college, "테스트 학과");
        Professor professor = new Professor(department, "테스트 교수", null);
        return new Laboratory(
            professor,
            department,
            "테스트 연구실",
            null,
            "인공지능",
            RecruitmentStatus.UNKNOWN
        );
    }
}
