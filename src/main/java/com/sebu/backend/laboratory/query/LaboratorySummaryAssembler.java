package com.sebu.backend.laboratory.query;

import com.sebu.backend.laboratory.dto.LaboratoriesResult;
import com.sebu.backend.laboratory.repository.LaboratorySummaryProjection;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LaboratorySummaryAssembler {

    public LaboratoriesResult.LaboratoryResult assemble(
            LaboratorySummaryProjection projection,
            List<String> researchFields
    ) {
        return assemble(
            projection,
            researchFields,
            List.of(),
            List.of(primaryAffiliation(projection))
        );
    }

    public LaboratoriesResult.LaboratoryResult assemble(
        LaboratorySummaryProjection projection,
        List<String> researchFields,
        List<LaboratoriesResult.AffiliationResult> affiliations
    ) {
        return assemble(
            projection,
            researchFields,
            List.of(),
            affiliations
        );
    }

    public LaboratoriesResult.LaboratoryResult assemble(
            LaboratorySummaryProjection projection,
            List<String> researchFields,
            List<LaboratoriesResult.ResearchFieldCategoryResult> researchFieldCategories,
            List<LaboratoriesResult.AffiliationResult> affiliations,
            long reviewCount
    ) {
        return assemble(
                projection,
                researchFields,
                List.of(),
                researchFieldCategories,
                affiliations,
                reviewCount
        );
    }

    public LaboratoriesResult.LaboratoryResult assembleWithResearchFieldDetails(
            LaboratorySummaryProjection projection,
            List<LaboratoriesResult.ResearchFieldResult> researchFieldDetails,
            List<LaboratoriesResult.ResearchFieldCategoryResult> researchFieldCategories,
            List<LaboratoriesResult.AffiliationResult> affiliations,
            long reviewCount
    ) {
        return assemble(
                projection,
                researchFieldDetails.stream()
                        .map(LaboratoriesResult.ResearchFieldResult::name)
                        .toList(),
                List.copyOf(researchFieldDetails),
                researchFieldCategories,
                affiliations,
                reviewCount
        );
    }

    private LaboratoriesResult.LaboratoryResult assemble(
            LaboratorySummaryProjection projection,
            List<String> researchFields,
            List<LaboratoriesResult.ResearchFieldResult> researchFieldDetails,
            List<LaboratoriesResult.ResearchFieldCategoryResult> researchFieldCategories,
            List<LaboratoriesResult.AffiliationResult> affiliations,
            long reviewCount
    ) {
        List<LaboratoriesResult.AffiliationResult> resolvedAffiliations =
                affiliations.isEmpty()
                        ? List.of(primaryAffiliation(projection))
                        : List.copyOf(affiliations);

        return new LaboratoriesResult.LaboratoryResult(
                projection.getId(),
                projection.getName(),
                projection.getNameSource(),
                projection.getWebsiteUrl(),

                new LaboratoriesResult.ProfessorResult(
                        projection.getProfessorId(),
                        projection.getProfessorName(),
                        projection.getProfessorEmail()
                ),

                new LaboratoriesResult.CollegeResult(
                        projection.getCollegeId(),
                        projection.getCollegeName()
                ),

                new LaboratoriesResult.DepartmentResult(
                        projection.getDepartmentId(),
                        projection.getDepartmentName()
                ),

                resolvedAffiliations,
                researchFields,
                researchFieldDetails,
                researchFieldCategories,
                projection.getRecruitmentStatus(),
                projection.getBookmarkCount(),
                Boolean.TRUE.equals(projection.getBookmarked()),
                reviewCount
        );
    }

    private LaboratoriesResult.AffiliationResult primaryAffiliation(
        LaboratorySummaryProjection projection
    ) {
        return new LaboratoriesResult.AffiliationResult(
            new LaboratoriesResult.CollegeResult(
                projection.getCollegeId(),
                projection.getCollegeName()
            ),
            new LaboratoriesResult.DepartmentResult(
                projection.getDepartmentId(),
                projection.getDepartmentName()
            )
        );
    }

    public LaboratoriesResult.LaboratoryResult assemble(
            LaboratorySummaryProjection projection,
            List<String> researchFields,
            List<LaboratoriesResult.ResearchFieldCategoryResult> researchFieldCategories,
            List<LaboratoriesResult.AffiliationResult> affiliations
    ) {
        return assemble(
                projection,
                researchFields,
                researchFieldCategories,
                affiliations,
                projection.getReviewCount() == null
                        ? 0L
                        : projection.getReviewCount()
        );
    }
}
