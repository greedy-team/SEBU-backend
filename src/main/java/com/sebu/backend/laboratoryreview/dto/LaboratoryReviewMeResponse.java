package com.sebu.backend.laboratoryreview.dto;

import com.sebu.backend.laboratoryreview.domain.Atmosphere;
import com.sebu.backend.laboratoryreview.domain.Compensation;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReview;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReviewCategory;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReviewTag;
import com.sebu.backend.laboratoryreview.domain.ParticipationTerm;
import com.sebu.backend.laboratoryreview.domain.ResearchIntensity;

import java.time.LocalDateTime;
import java.util.Set;

public record LaboratoryReviewMeResponse(
        Long id,
        LaboratoryReviewCategory category,
        ResearchIntensity researchIntensity,
        Compensation compensation,
        Atmosphere atmosphere,
        Set<LaboratoryReviewTag> tags,
        String content,
        int participationYear,
        ParticipationTerm participationTerm,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static LaboratoryReviewMeResponse from(
            LaboratoryReview review
    ) {
        return new LaboratoryReviewMeResponse(
                review.getId(),
                review.getCategory(),
                review.getResearchIntensity(),
                review.getCompensation(),
                review.getAtmosphere(),
                Set.copyOf(review.getTags()),
                review.getContent(),
                review.getParticipationYear(),
                review.getParticipationTerm(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
