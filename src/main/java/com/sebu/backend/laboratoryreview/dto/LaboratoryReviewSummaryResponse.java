package com.sebu.backend.laboratoryreview.dto;

import java.util.List;

public record LaboratoryReviewSummaryResponse(
        LaboratoryInfo laboratory,
        long reviewCount,
        EvaluationDistributions evaluationDistributions
) {

    public record LaboratoryInfo(
            Long id,
            String name,
            ProfessorInfo professor,
            CollegeInfo college,
            DepartmentInfo department
    ) {
    }

    public record ProfessorInfo(
            Long id,
            String name
    ) {
    }

    public record CollegeInfo(
            Long id,
            String name
    ) {
    }

    public record DepartmentInfo(
            Long id,
            String name
    ) {
    }

    public record EvaluationDistribution(
            String value,
            long count,
            double percentage
    ) {
    }

    public record EvaluationDistributions(
            List<EvaluationDistribution> researchIntensity,
            List<EvaluationDistribution> compensation,
            List<EvaluationDistribution> atmosphere
    ) {
    }
}
