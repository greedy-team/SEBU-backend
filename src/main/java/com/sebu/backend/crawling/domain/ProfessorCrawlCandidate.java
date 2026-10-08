package com.sebu.backend.crawling.domain;

import com.sebu.backend.global.domain.BaseTimeEntity;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.professor.domain.Professor;
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
    name = "professor_crawl_candidate",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_professor_crawl_candidate_source_identity",
            columnNames = {"source_id", "source_identity_key"}
        )
    },
    indexes = {
        @Index(name = "idx_professor_crawl_candidate_review_status", columnList = "review_status"),
        @Index(name = "idx_professor_crawl_candidate_email", columnList = "email"),
        @Index(
            name = "idx_professor_crawl_candidate_source_name",
            columnList = "source_id, professor_name"
        ),
        @Index(
            name = "idx_professor_crawl_candidate_current_review",
            columnList = "is_stale, review_status"
        ),
        @Index(
            name = "idx_professor_crawl_candidate_promotion",
            columnList = "source_id, review_status, is_stale, id"
        ),
        @Index(
            name = "idx_professor_crawl_candidate_promoted_professor",
            columnList = "promoted_professor_id"
        ),
        @Index(
            name = "idx_professor_crawl_candidate_promoted_laboratory",
            columnList = "promoted_laboratory_id"
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfessorCrawlCandidate extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private CrawlSource source;

    @Column(name = "source_identity_key", nullable = false, length = 320)
    private String sourceIdentityKey;

    @Column(name = "professor_name", nullable = false, length = 100)
    private String professorName;

    @Column(length = 100)
    private String position;

    @Column(length = 255)
    private String email;

    @Column(name = "laboratory_name", length = 150)
    private String laboratoryName;

    @Column(name = "research_introduction", length = 2000)
    private String researchIntroduction;

    @Column(name = "homepage_url", length = 2048)
    private String homepageUrl;

    @Column(name = "source_url_at_crawl", nullable = false, length = 512)
    private String sourceUrlAtCrawl;

    @Enumerated(EnumType.STRING)
    @Column(name = "parser_type_at_crawl", nullable = false, length = 50)
    private CrawlParserType parserTypeAtCrawl;

    @Column(name = "is_stale", nullable = false)
    private boolean stale;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private CandidateReviewStatus reviewStatus = CandidateReviewStatus.PENDING;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_revision", nullable = false)
    private long reviewRevision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoted_professor_id")
    private Professor promotedProfessor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoted_laboratory_id")
    private Laboratory promotedLaboratory;

    @Column(name = "promoted_at")
    private LocalDateTime promotedAt;

    @Column(name = "promoted_reviewed_at")
    private LocalDateTime promotedReviewedAt;

    @Column(name = "promoted_review_revision")
    private Long promotedReviewRevision;

    @Column(name = "crawled_at", nullable = false)
    private LocalDateTime crawledAt;

    public ProfessorCrawlCandidate(
        CrawlSource source,
        ProfessorCrawlData data,
        CrawlSourceProvenance provenance,
        LocalDateTime crawledAt
    ) {
        this.source = Objects.requireNonNull(source, "SOURCE_REQUIRED");
        ProfessorCrawlData normalizedData = Objects.requireNonNull(data, "CRAWL_DATA_REQUIRED");
        sourceIdentityKey = normalizedData.identityKey();
        apply(normalizedData);
        captureProvenance(provenance);
        this.crawledAt = Objects.requireNonNull(crawledAt, "CRAWLED_AT_REQUIRED");
    }

    public void refreshFromCrawl(
        ProfessorCrawlData data,
        CrawlSourceProvenance provenance,
        LocalDateTime crawledAt
    ) {
        ProfessorCrawlData normalizedData = Objects.requireNonNull(data, "CRAWL_DATA_REQUIRED");
        CrawlSourceProvenance normalizedProvenance = Objects.requireNonNull(
            provenance,
            "CRAWL_PROVENANCE_REQUIRED"
        );
        LocalDateTime normalizedCrawledAt = Objects.requireNonNull(crawledAt, "CRAWLED_AT_REQUIRED");
        boolean changed = stale || hasChanges(normalizedData)
            || provenanceChanged(normalizedProvenance);

        apply(normalizedData);
        captureProvenance(normalizedProvenance);
        stale = false;
        this.crawledAt = normalizedCrawledAt;
        if (changed) {
            resetReview();
        }
    }

    public void reidentify(String identityKey) {
        sourceIdentityKey = requireText(identityKey, "CRAWL_IDENTITY_REQUIRED");
    }

    public void revise(ProfessorCrawlData data) {
        ensureReviewable();
        apply(data);
    }

    public void markStale() {
        stale = true;
    }

    public void approve(String reviewer, String note, LocalDateTime reviewedAt) {
        review(CandidateReviewStatus.APPROVED, reviewer, note, reviewedAt);
    }

    public void reject(String reviewer, String note, LocalDateTime reviewedAt) {
        review(CandidateReviewStatus.REJECTED, reviewer, note, reviewedAt);
    }

    public boolean isCurrentAndApproved() {
        return !stale && reviewStatus == CandidateReviewStatus.APPROVED;
    }

    public boolean needsPromotion() {
        return isCurrentAndApproved()
            && reviewedAt != null
            && reviewRevision > 0
            && !Objects.equals(reviewRevision, promotedReviewRevision);
    }

    public boolean hasBeenPromoted() {
        return promotedAt != null
            || promotedReviewedAt != null
            || promotedReviewRevision != null
            || promotedProfessor != null
            || promotedLaboratory != null;
    }

    public boolean hasConsistentPromotionState() {
        boolean unpromoted = promotedAt == null
            && promotedReviewedAt == null
            && promotedReviewRevision == null
            && promotedProfessor == null
            && promotedLaboratory == null;
        boolean promoted = promotedAt != null
            && promotedReviewedAt != null
            && promotedReviewRevision != null
            && promotedProfessor != null;
        return unpromoted || promoted;
    }

    public void recordPromotion(
        Professor professor,
        Laboratory laboratory,
        LocalDateTime promotedAt
    ) {
        if (!needsPromotion()) {
            throw new IllegalStateException("CANDIDATE_NOT_READY_FOR_PROMOTION");
        }
        Professor normalizedProfessor = Objects.requireNonNull(
            professor,
            "PROMOTED_PROFESSOR_REQUIRED"
        );
        Laboratory normalizedLaboratory = Objects.requireNonNull(
            laboratory,
            "PROMOTED_LABORATORY_REQUIRED"
        );
        if (hasBeenPromoted()
            && (!sameEntity(this.promotedProfessor, normalizedProfessor)
                || !sameEntity(this.promotedLaboratory, normalizedLaboratory))) {
            throw new IllegalArgumentException("PROMOTED_ENTITY_CANNOT_BE_REPLACED");
        }

        this.promotedProfessor = normalizedProfessor;
        this.promotedLaboratory = normalizedLaboratory;
        this.promotedAt = Objects.requireNonNull(promotedAt, "PROMOTED_AT_REQUIRED");
        promotedReviewedAt = reviewedAt;
        promotedReviewRevision = reviewRevision;
    }

    private boolean sameEntity(Professor current, Professor requested) {
        return current == requested
            || current != null
            && current.getId() != null
            && Objects.equals(current.getId(), requested.getId());
    }

    private boolean sameEntity(Laboratory current, Laboratory requested) {
        return current == requested
            || current != null
            && current.getId() != null
            && Objects.equals(current.getId(), requested.getId());
    }

    private void review(
        CandidateReviewStatus decision,
        String reviewer,
        String note,
        LocalDateTime reviewedAt
    ) {
        ensureReviewable();
        String normalizedReviewer = requireText(reviewer, "REVIEWER_REQUIRED");
        String normalizedNote = normalizeNullable(note);
        LocalDateTime normalizedReviewedAt = Objects.requireNonNull(reviewedAt, "REVIEWED_AT_REQUIRED");

        reviewStatus = decision;
        reviewedBy = normalizedReviewer;
        reviewNote = normalizedNote;
        this.reviewedAt = normalizedReviewedAt;
        reviewRevision++;
    }

    private boolean hasChanges(ProfessorCrawlData data) {
        return !currentData().equals(data);
    }

    private boolean provenanceChanged(CrawlSourceProvenance provenance) {
        return !Objects.equals(sourceUrlAtCrawl, provenance.sourceUrl())
            || parserTypeAtCrawl != provenance.parserType();
    }

    private void captureProvenance(CrawlSourceProvenance provenance) {
        CrawlSourceProvenance normalized = Objects.requireNonNull(
            provenance,
            "CRAWL_PROVENANCE_REQUIRED"
        );
        sourceUrlAtCrawl = normalized.sourceUrl();
        parserTypeAtCrawl = normalized.parserType();
    }

    private ProfessorCrawlData currentData() {
        return new ProfessorCrawlData(
            professorName,
            position,
            email,
            laboratoryName,
            researchIntroduction,
            homepageUrl
        );
    }

    private void apply(ProfessorCrawlData data) {
        ProfessorCrawlData normalizedData = Objects.requireNonNull(data, "CRAWL_DATA_REQUIRED");
        professorName = normalizedData.professorName();
        position = normalizedData.position();
        email = normalizedData.email();
        laboratoryName = normalizedData.laboratoryName();
        researchIntroduction = normalizedData.researchIntroduction();
        homepageUrl = normalizedData.homepageUrl();
    }

    private void resetReview() {
        reviewStatus = CandidateReviewStatus.PENDING;
        reviewNote = null;
        reviewedBy = null;
        reviewedAt = null;
    }

    private void ensureReviewable() {
        if (stale) {
            throw new IllegalStateException("STALE_CANDIDATE_NOT_REVIEWABLE");
        }
        if (reviewStatus != CandidateReviewStatus.PENDING) {
            throw new IllegalStateException("CANDIDATE_ALREADY_REVIEWED");
        }
    }

    private String requireText(String value, String errorCode) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(errorCode);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
