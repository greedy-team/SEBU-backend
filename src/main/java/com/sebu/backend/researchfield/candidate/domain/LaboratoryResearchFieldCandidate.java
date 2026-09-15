package com.sebu.backend.researchfield.candidate.domain;

import com.sebu.backend.global.domain.BaseTimeEntity;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.researchfield.domain.ResearchField;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(
    name = "laboratory_research_field_candidate",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_laboratory_research_field_candidate_identity",
        columnNames = {"laboratory_id", "source_field_key"}
    ),
    indexes = {
        @Index(
            name = "idx_lrf_candidate_current_review",
            columnList = "is_stale, review_status, id"
        ),
        @Index(
            name = "idx_lrf_candidate_split_origin",
            columnList = "split_from_candidate_id, id"
        ),
        @Index(
            name = "idx_lrf_candidate_promoted_research_field",
            columnList = "promoted_research_field_id"
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LaboratoryResearchFieldCandidate extends BaseTimeEntity {
    private static final int HASH_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "laboratory_id", nullable = false)
    private Laboratory laboratory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "split_from_candidate_id")
    private LaboratoryResearchFieldCandidate splitFromCandidate;

    @Column(name = "source_field_key", nullable = false, length = HASH_LENGTH)
    private String sourceFieldKey;

    @Column(name = "source_description_hash", nullable = false, length = HASH_LENGTH)
    private String sourceDescriptionHash;

    @Column(name = "raw_field_text", nullable = false, length = 2000)
    private String rawFieldText;

    @Column(name = "candidate_name", length = 100)
    private String candidateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_method", nullable = false, length = 30)
    private ResearchFieldExtractionMethod extractionMethod;

    @Column(name = "source_order", nullable = false)
    private int sourceOrder;

    @Column(name = "extraction_rule_version", nullable = false, length = 30)
    private String extractionRuleVersion;

    @Column(name = "is_stale", nullable = false)
    private boolean stale;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private ResearchFieldCandidateReviewStatus reviewStatus =
        ResearchFieldCandidateReviewStatus.PENDING;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_revision", nullable = false)
    private long reviewRevision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoted_research_field_id")
    private ResearchField promotedResearchField;

    @Column(name = "promoted_at")
    private LocalDateTime promotedAt;

    @Column(name = "promoted_reviewed_at")
    private LocalDateTime promotedReviewedAt;

    @Column(name = "promoted_review_revision")
    private Long promotedReviewRevision;

    @Column(name = "extracted_at", nullable = false)
    private LocalDateTime extractedAt;

    public LaboratoryResearchFieldCandidate(
        Laboratory laboratory,
        ResearchFieldCandidateDraft draft,
        String sourceDescriptionHash,
        String extractionRuleVersion,
        LocalDateTime extractedAt
    ) {
        this.laboratory = Objects.requireNonNull(laboratory, "LABORATORY_REQUIRED");
        applyDraft(Objects.requireNonNull(draft, "CANDIDATE_DRAFT_REQUIRED"));
        this.sourceDescriptionHash = requireHash(
            sourceDescriptionHash,
            "SOURCE_DESCRIPTION_HASH_INVALID"
        );
        this.extractionRuleVersion = requireText(
            extractionRuleVersion,
            30,
            "EXTRACTION_RULE_VERSION_INVALID"
        );
        this.extractedAt = Objects.requireNonNull(extractedAt, "EXTRACTED_AT_REQUIRED");
    }

    public static LaboratoryResearchFieldCandidate manualSplit(
        LaboratoryResearchFieldCandidate source,
        ResearchFieldCandidateDraft draft,
        String extractionRuleVersion,
        LocalDateTime extractedAt
    ) {
        LaboratoryResearchFieldCandidate normalizedSource = Objects.requireNonNull(
            source,
            "MANUAL_SPLIT_SOURCE_REQUIRED"
        );
        normalizedSource.ensureManualSplitSourceReviewable();
        ResearchFieldCandidateDraft normalizedDraft = Objects.requireNonNull(
            draft,
            "CANDIDATE_DRAFT_REQUIRED"
        );
        if (normalizedDraft.extractionMethod()
            != ResearchFieldExtractionMethod.MANUAL_SPLIT) {
            throw new IllegalArgumentException("MANUAL_SPLIT_METHOD_REQUIRED");
        }
        LaboratoryResearchFieldCandidate candidate =
            new LaboratoryResearchFieldCandidate(
                normalizedSource.laboratory,
                normalizedDraft,
                normalizedSource.sourceDescriptionHash,
                extractionRuleVersion,
                extractedAt
            );
        candidate.splitFromCandidate = normalizedSource;
        return candidate;
    }

    public boolean refreshFromExtraction(
        ResearchFieldCandidateDraft draft,
        String sourceDescriptionHash,
        String extractionRuleVersion,
        LocalDateTime extractedAt
    ) {
        ResearchFieldCandidateDraft normalizedDraft = Objects.requireNonNull(
            draft,
            "CANDIDATE_DRAFT_REQUIRED"
        );
        if (!Objects.equals(sourceFieldKey, normalizedDraft.sourceFieldKey())) {
            throw new IllegalArgumentException("SOURCE_FIELD_KEY_CANNOT_CHANGE");
        }
        String normalizedDescriptionHash = requireHash(
            sourceDescriptionHash,
            "SOURCE_DESCRIPTION_HASH_INVALID"
        );
        String normalizedRuleVersion = requireText(
            extractionRuleVersion,
            30,
            "EXTRACTION_RULE_VERSION_INVALID"
        );
        LocalDateTime normalizedExtractedAt = Objects.requireNonNull(
            extractedAt,
            "EXTRACTED_AT_REQUIRED"
        );
        boolean changed = stale
            || !Objects.equals(rawFieldText, normalizedDraft.rawFieldText())
            || extractionMethod != normalizedDraft.extractionMethod()
            || sourceOrder != normalizedDraft.sourceOrder()
            || !Objects.equals(this.sourceDescriptionHash, normalizedDescriptionHash)
            || !Objects.equals(this.extractionRuleVersion, normalizedRuleVersion);

        if (!changed) {
            return false;
        }
        boolean hasUnmodifiedAutomaticName =
            reviewStatus == ResearchFieldCandidateReviewStatus.PENDING
                && Objects.equals(candidateName, rawFieldText);
        if (stale) {
            resetReview();
        }
        if (hasUnmodifiedAutomaticName) {
            candidateName = normalizedDraft.candidateName();
        }
        rawFieldText = normalizedDraft.rawFieldText();
        extractionMethod = normalizedDraft.extractionMethod();
        sourceOrder = normalizedDraft.sourceOrder();
        this.sourceDescriptionHash = normalizedDescriptionHash;
        this.extractionRuleVersion = normalizedRuleVersion;
        this.extractedAt = normalizedExtractedAt;
        stale = false;
        return true;
    }

    public boolean markStale() {
        if (stale) {
            return false;
        }
        stale = true;
        return true;
    }

    public void reviseCandidateName(String candidateName) {
        ensureReviewable();
        this.candidateName = normalizeNullable(candidateName, 100, "CANDIDATE_NAME_INVALID");
    }

    public void approve(String reviewer, String note, LocalDateTime reviewedAt) {
        if (candidateName == null) {
            throw new IllegalStateException("CANDIDATE_NAME_REQUIRED_FOR_APPROVAL");
        }
        review(ResearchFieldCandidateReviewStatus.APPROVED, reviewer, note, reviewedAt);
    }

    public void reject(String reviewer, String note, LocalDateTime reviewedAt) {
        review(ResearchFieldCandidateReviewStatus.REJECTED, reviewer, note, reviewedAt);
    }

    public void rejectAfterManualSplit(
        String reviewer,
        String note,
        LocalDateTime reviewedAt
    ) {
        ensureManualSplitSourceReviewable();
        review(ResearchFieldCandidateReviewStatus.REJECTED, reviewer, note, reviewedAt);
    }

    public boolean isManualSplit() {
        return extractionMethod == ResearchFieldExtractionMethod.MANUAL_SPLIT;
    }

    public boolean shouldBecomeStaleFromSplitSource() {
        if (!isManualSplit() || splitFromCandidate == null) {
            return false;
        }
        return splitFromCandidate.stale
            || !Objects.equals(
                sourceDescriptionHash,
                splitFromCandidate.sourceDescriptionHash
            );
    }

    public boolean isCurrentAndApproved() {
        return !stale && reviewStatus == ResearchFieldCandidateReviewStatus.APPROVED;
    }

    public boolean needsPromotion() {
        return isCurrentAndApproved()
            && candidateName != null
            && reviewedBy != null
            && !reviewedBy.isBlank()
            && reviewedAt != null
            && reviewRevision > 0
            && !Objects.equals(reviewRevision, promotedReviewRevision);
    }

    public boolean hasBeenPromoted() {
        return promotedResearchField != null
            || promotedAt != null
            || promotedReviewedAt != null
            || promotedReviewRevision != null;
    }

    public boolean hasConsistentPromotionState() {
        boolean unpromoted = promotedResearchField == null
            && promotedAt == null
            && promotedReviewedAt == null
            && promotedReviewRevision == null;
        boolean promoted = promotedResearchField != null
            && promotedAt != null
            && promotedReviewedAt != null
            && promotedReviewRevision != null;
        return unpromoted || promoted;
    }

    public void recordPromotion(
        ResearchField researchField,
        LocalDateTime promotedAt
    ) {
        if (!needsPromotion()) {
            throw new IllegalStateException("CANDIDATE_NOT_READY_FOR_PROMOTION");
        }
        ResearchField normalizedResearchField = Objects.requireNonNull(
            researchField,
            "PROMOTED_RESEARCH_FIELD_REQUIRED"
        );
        LocalDateTime normalizedPromotedAt = Objects.requireNonNull(
            promotedAt,
            "PROMOTED_AT_REQUIRED"
        );
        if (hasBeenPromoted()
            && !sameEntity(promotedResearchField, normalizedResearchField)) {
            throw new IllegalArgumentException("PROMOTED_ENTITY_CANNOT_BE_REPLACED");
        }

        this.promotedResearchField = normalizedResearchField;
        this.promotedAt = normalizedPromotedAt;
        promotedReviewedAt = reviewedAt;
        promotedReviewRevision = reviewRevision;
    }

    private boolean sameEntity(ResearchField current, ResearchField requested) {
        return current == requested
            || current != null
            && current.getId() != null
            && Objects.equals(current.getId(), requested.getId());
    }

    private void review(
        ResearchFieldCandidateReviewStatus decision,
        String reviewer,
        String note,
        LocalDateTime reviewedAt
    ) {
        ensureReviewable();
        reviewStatus = Objects.requireNonNull(decision, "REVIEW_DECISION_REQUIRED");
        reviewedBy = requireText(reviewer, 100, "REVIEWER_REQUIRED");
        reviewNote = normalizeNullable(note, 1000, "REVIEW_NOTE_INVALID");
        this.reviewedAt = Objects.requireNonNull(reviewedAt, "REVIEWED_AT_REQUIRED");
        reviewRevision++;
    }

    private void ensureReviewable() {
        if (stale) {
            throw new IllegalStateException("STALE_CANDIDATE_NOT_REVIEWABLE");
        }
        if (reviewStatus != ResearchFieldCandidateReviewStatus.PENDING) {
            throw new IllegalStateException("CANDIDATE_ALREADY_REVIEWED");
        }
    }

    private void ensureManualSplitSourceReviewable() {
        ensureReviewable();
        if (isManualSplit()) {
            throw new IllegalStateException("AUTOMATIC_SPLIT_SOURCE_REQUIRED");
        }
        if (hasBeenPromoted()) {
            throw new IllegalStateException("PROMOTED_SOURCE_CANNOT_BE_SPLIT");
        }
    }

    private void applyDraft(ResearchFieldCandidateDraft draft) {
        sourceFieldKey = draft.sourceFieldKey();
        rawFieldText = draft.rawFieldText();
        candidateName = draft.candidateName();
        extractionMethod = draft.extractionMethod();
        sourceOrder = draft.sourceOrder();
    }

    private void resetReview() {
        reviewStatus = ResearchFieldCandidateReviewStatus.PENDING;
        reviewedBy = null;
        reviewNote = null;
        reviewedAt = null;
    }

    private String requireHash(String value, String errorCode) {
        String normalized = requireText(value, HASH_LENGTH, errorCode);
        if (normalized.length() != HASH_LENGTH
            || !normalized.matches("[0-9a-f]{" + HASH_LENGTH + "}")) {
            throw new IllegalArgumentException(errorCode);
        }
        return normalized;
    }

    private String requireText(String value, int maxLength, String errorCode) {
        String normalized = normalizeNullable(value, maxLength, errorCode);
        if (normalized == null) {
            throw new IllegalArgumentException(errorCode);
        }
        return normalized;
    }

    private String normalizeNullable(String value, int maxLength, String errorCode) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(errorCode);
        }
        return normalized;
    }
}
