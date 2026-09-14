package com.sebu.backend.laboratory.dto;

import com.sebu.backend.laboratory.domain.LaboratoryNameSource;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;

import java.util.List;

public record LaboratoriesResult(
        List<LaboratoryResult> laboratories
) {

    public record LaboratoryResult(
            Long id,
            String name,
            LaboratoryNameSource nameSource,
            String websiteUrl,
            ProfessorResult professor,
            CollegeResult college,
            DepartmentResult department,
            List<AffiliationResult> affiliations,
            List<String> researchFields,
            List<ResearchFieldResult> researchFieldDetails,
            List<ResearchFieldCategoryResult> researchFieldCategories,
            RecruitmentStatus recruitmentStatus,
            long bookmarkCount,
            boolean bookmarked,
            long reviewCount
    ) {
    }

    public record ProfessorResult(
            Long id,
            String name,
            String email
    ) {
    }

    public record CollegeResult(
            Long id,
            String name
    ) {
    }

    public record DepartmentResult(
            Long id,
            String name
    ) {
    }

    public record ResearchFieldResult(
            Long researchFieldId,
            String name,
            List<Long> categoryIds
    ) {
        public ResearchFieldResult {
            categoryIds = List.copyOf(categoryIds);
        }
    }

    public record ResearchFieldCategoryResult(
            Long id,
            String code,
            String name
    ) {
    }

    public record AffiliationResult(
            CollegeResult college,
            DepartmentResult department
    ) {
    }
}
