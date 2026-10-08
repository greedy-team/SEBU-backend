package com.sebu.backend.laboratoryreview.domain;

import com.sebu.backend.global.domain.BaseTimeEntity;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratoryreview.exception.InvalidLaboratoryReviewInputException;
import com.sebu.backend.user.domain.AppUser;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Getter
@Entity
@Table(name = "laboratory_review")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LaboratoryReview extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "laboratory_id", nullable = false)
    private Laboratory laboratory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LaboratoryReviewCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "research_intensity", nullable = false, length = 30)
    private ResearchIntensity researchIntensity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Compensation compensation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Atmosphere atmosphere;

    @Getter(AccessLevel.NONE)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "laboratory_review_tag",
            joinColumns = @JoinColumn(name = "review_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "tag", nullable = false, length = 50)
    private Set<LaboratoryReviewTag> tags = new LinkedHashSet<>();

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "participation_year", nullable = false)
    private int participationYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "participation_term", nullable = false, length = 30)
    private ParticipationTerm participationTerm;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public LaboratoryReview(
            Laboratory laboratory,
            AppUser author,
            LaboratoryReviewCategory category,
            ResearchIntensity researchIntensity,
            Compensation compensation,
            Atmosphere atmosphere,
            Set<LaboratoryReviewTag> tags,
            String content,
            int participationYear,
            ParticipationTerm participationTerm
    ) {
        this.laboratory = Objects.requireNonNull(
                laboratory,
                "LABORATORY_REQUIRED"
        );

        this.author = Objects.requireNonNull(
                author,
                "AUTHOR_REQUIRED"
        );

        applyReview(
                category,
                researchIntensity,
                compensation,
                atmosphere,
                tags,
                content,
                participationYear,
                participationTerm
        );
    }

    public void update(
            LaboratoryReviewCategory category,
            ResearchIntensity researchIntensity,
            Compensation compensation,
            Atmosphere atmosphere,
            Set<LaboratoryReviewTag> tags,
            String content,
            int participationYear,
            ParticipationTerm participationTerm
    ) {
        if (isDeleted()) {
            throw new IllegalStateException(
                    "DELETED_LABORATORY_REVIEW_CANNOT_BE_UPDATED"
            );
        }

        applyReview(
                category,
                researchIntensity,
                compensation,
                atmosphere,
                tags,
                content,
                participationYear,
                participationTerm
        );
    }

    public void softDelete() {
        if (deletedAt == null) {
            deletedAt = LocalDateTime.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isWrittenBy(Long userId) {
        return Objects.equals(author.getId(), userId);
    }

    /*
     * 내부의 변경 가능한 Set을 그대로 노출하지 않는다.
     */
    public Set<LaboratoryReviewTag> getTags() {
        return Set.copyOf(tags);
    }

    /*
     * 태그 변경은 검증을 거쳐서만 수행한다.
     */
    public void replaceTags(Set<LaboratoryReviewTag> tags) {
        if (isDeleted()) {
            throw new IllegalStateException(
                    "DELETED_LABORATORY_REVIEW_CANNOT_BE_UPDATED"
            );
        }

        this.tags = normalizeTags(tags);
    }

    private void applyReview(
            LaboratoryReviewCategory category,
            ResearchIntensity researchIntensity,
            Compensation compensation,
            Atmosphere atmosphere,
            Set<LaboratoryReviewTag> tags,
            String content,
            int participationYear,
            ParticipationTerm participationTerm
    ) {
        LaboratoryReviewCategory validatedCategory = Objects.requireNonNull(
                category,
                "LABORATORY_REVIEW_CATEGORY_REQUIRED"
        );

        ResearchIntensity validatedResearchIntensity = Objects.requireNonNull(
                researchIntensity,
                "RESEARCH_INTENSITY_REQUIRED"
        );

        Compensation validatedCompensation = Objects.requireNonNull(
                compensation,
                "COMPENSATION_REQUIRED"
        );

        Atmosphere validatedAtmosphere = Objects.requireNonNull(
                atmosphere,
                "ATMOSPHERE_REQUIRED"
        );

        Set<LaboratoryReviewTag> normalizedTags = normalizeTags(tags);

        validateParticipationYear(participationYear);

        String normalizedContent = normalizeContent(content);

        ParticipationTerm validatedParticipationTerm = Objects.requireNonNull(
                participationTerm,
                "PARTICIPATION_TERM_REQUIRED"
        );

        this.category = validatedCategory;
        this.researchIntensity = validatedResearchIntensity;
        this.compensation = validatedCompensation;
        this.atmosphere = validatedAtmosphere;
        this.tags = normalizedTags;
        this.content = normalizedContent;
        this.participationYear = participationYear;
        this.participationTerm = validatedParticipationTerm;
    }

    private Set<LaboratoryReviewTag> normalizeTags(
            Set<LaboratoryReviewTag> tags
    ) {
        if (tags == null || tags.isEmpty()) {
            return new LinkedHashSet<>();
        }

        if (tags.stream().anyMatch(Objects::isNull)) {
            throw InvalidLaboratoryReviewInputException.invalidTag();
        }

        return new LinkedHashSet<>(tags);
    }

    private void validateParticipationYear(
            int participationYear
    ) {
        int currentYear = Year.now().getValue();

        if (participationYear < 2000 || participationYear > currentYear) {
            throw InvalidLaboratoryReviewInputException
                    .invalidParticipationYear(currentYear);
        }
    }

    private String normalizeContent(
            String content
    ) {
        if (content == null) {
            throw InvalidLaboratoryReviewInputException.invalidContent();
        }

        String normalized = content.trim();

        if (normalized.length() < 20 || normalized.length() > 2000) {
            throw InvalidLaboratoryReviewInputException.invalidContent();
        }

        return normalized;
    }
}
