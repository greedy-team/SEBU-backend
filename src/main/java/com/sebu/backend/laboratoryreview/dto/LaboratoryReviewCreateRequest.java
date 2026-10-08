package com.sebu.backend.laboratoryreview.dto;

import com.sebu.backend.laboratoryreview.domain.Atmosphere;
import com.sebu.backend.laboratoryreview.domain.Compensation;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReviewCategory;
import com.sebu.backend.laboratoryreview.domain.LaboratoryReviewTag;
import com.sebu.backend.laboratoryreview.domain.ParticipationTerm;
import com.sebu.backend.laboratoryreview.domain.ResearchIntensity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record LaboratoryReviewCreateRequest(

        @NotNull
        LaboratoryReviewCategory category,

        @NotNull
        ResearchIntensity researchIntensity,

        @NotNull
        Compensation compensation,

        @NotNull
        Atmosphere atmosphere,

        Set<@NotNull LaboratoryReviewTag> tags,

        @NotBlank
        @Size(min = 20, max = 2000)
        String content,

        @Min(2000)
        int participationYear,

        @NotNull
        ParticipationTerm participationTerm
) {
}
