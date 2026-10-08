package com.sebu.backend.laboratoryreview.dto;

import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReview;

import java.time.LocalDateTime;
import java.util.List;

public record LaboratoryReviewListResponse(
        LaboratoryInfo laboratory,
        boolean reviewedByMe,
        List<ReviewItem> reviews,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {

    public record LaboratoryInfo(
            Long id,
            String name
    ) {
        public static LaboratoryInfo from(
                Laboratory laboratory
        ) {
            return new LaboratoryInfo(
                    laboratory.getId(),
                    laboratory.getName()
            );
        }
    }

    public record ReviewItem(
            Long id,
            String category,
            int participationYear,
            String participationTerm,
            String researchIntensity,
            String compensation,
            String atmosphere,
            List<String> tags,
            String content,
            LocalDateTime createdAt
    ) {

        public static ReviewItem from(
                LaboratoryReview review,
                List<String> tags
        ) {
            return new ReviewItem(
                    review.getId(),
                    review.getCategory().name(),
                    review.getParticipationYear(),
                    review.getParticipationTerm().name(),
                    review.getResearchIntensity().name(),
                    review.getCompensation().name(),
                    review.getAtmosphere().name(),
                    tags,
                    review.getContent(),
                    review.getCreatedAt()
            );
        }
    }
}
