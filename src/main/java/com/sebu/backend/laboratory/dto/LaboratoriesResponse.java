package com.sebu.backend.laboratory.dto;

import com.sebu.backend.laboratory.domain.LaboratoryNameSource;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record LaboratoriesResponse(
        List<LaboratoryResponse> laboratories
) {

    public static LaboratoriesResponse from(
            LaboratoriesResult result
    ) {
        List<LaboratoryResponse> laboratories =
                result.laboratories()
                        .stream()
                        .map(LaboratoryResponse::from)
                        .toList();

        return new LaboratoriesResponse(laboratories);
    }

    public record LaboratoryResponse(
            Long id,
            String name,
            LaboratoryNameSource nameSource,
            String websiteUrl,
            ProfessorResponse professor,
            CollegeResponse college,
            DepartmentResponse department,
            List<AffiliationResponse> affiliations,
            List<String> researchFields,
            @Schema(description = "해당 연구실의 연구 분야 ID, 이름 및 분야별 카테고리 ID 목록")
            List<ResearchFieldDetailResponse> researchFieldDetails,
            List<Long> researchFieldCategoryIds,
            List<ResearchFieldCategoryResponse> researchFieldCategories,
            RecruitmentStatus recruitmentStatus,
            long bookmarkCount,
            boolean bookmarked,
            long reviewCount
    ) {

        public static LaboratoryResponse from(
                LaboratoriesResult.LaboratoryResult result
        ) {
            return new LaboratoryResponse(
                    result.id(),
                    result.name(),
                    result.nameSource(),
                    result.websiteUrl(),

                    new ProfessorResponse(
                            result.professor().id(),
                            result.professor().name(),
                            result.professor().email()
                    ),

                    new CollegeResponse(
                            result.college().id(),
                            result.college().name()
                    ),

                    new DepartmentResponse(
                            result.department().id(),
                            result.department().name()
                    ),

                    result.affiliations()
                            .stream()
                            .map(AffiliationResponse::from)
                            .toList(),

                    result.researchFields(),

                    result.researchFieldDetails()
                            .stream()
                            .map(ResearchFieldDetailResponse::from)
                            .toList(),

                    result.researchFieldCategories()
                            .stream()
                            .map(
                                    LaboratoriesResult
                                            .ResearchFieldCategoryResult::id
                            )
                            .toList(),

                    result.researchFieldCategories()
                            .stream()
                            .map(ResearchFieldCategoryResponse::from)
                            .toList(),

                    result.recruitmentStatus(),
                    result.bookmarkCount(),
                    result.bookmarked(),
                    result.reviewCount()
            );
        }
    }

    public record ProfessorResponse(
            Long id,
            String name,
            String email
    ) {
    }

    public record CollegeResponse(
            Long id,
            String name
    ) {
    }

    public record DepartmentResponse(
            Long id,
            String name
    ) {
    }

    @Schema(name = "LaboratoryResearchFieldDetailResponse")
    public record ResearchFieldDetailResponse(
            @Schema(description = "연구 분야 고유 ID", example = "102",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            Long researchFieldId,
            @Schema(description = "연구 분야 이름", example = "머신러닝",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String name,
            @Schema(description = "해당 분야의 카테고리 ID 목록. 표시 순서로 정렬하며 미분류이면 빈 배열",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            List<Long> categoryIds
    ) {
        private static ResearchFieldDetailResponse from(
                LaboratoriesResult.ResearchFieldResult field
        ) {
            return new ResearchFieldDetailResponse(
                    field.researchFieldId(),
                    field.name(),
                    field.categoryIds()
            );
        }
    }

    public record ResearchFieldCategoryResponse(
            Long id,
            String code,
            String name
    ) {

        private static ResearchFieldCategoryResponse from(
                LaboratoriesResult.ResearchFieldCategoryResult category
        ) {
            return new ResearchFieldCategoryResponse(
                    category.id(),
                    category.code(),
                    category.name()
            );
        }
    }

    public record AffiliationResponse(
            CollegeResponse college,
            DepartmentResponse department
    ) {

        private static AffiliationResponse from(
                LaboratoriesResult.AffiliationResult affiliation
        ) {
            return new AffiliationResponse(
                    new CollegeResponse(
                            affiliation.college().id(),
                            affiliation.college().name()
                    ),
                    new DepartmentResponse(
                            affiliation.department().id(),
                            affiliation.department().name()
                    )
            );
        }
    }
}
