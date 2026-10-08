package com.sebu.backend.laboratoryreview.dto;

import java.time.LocalDateTime;

public record LaboratoryReviewUpdateResponse(
        Long reviewId,
        LocalDateTime updatedAt
) {
}
