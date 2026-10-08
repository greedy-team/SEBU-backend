package com.sebu.backend.researchfield.category.dto;

import java.util.List;

public record ResearchFieldCategoriesResponse(List<CategoryResponse> categories) {
    public static ResearchFieldCategoriesResponse from(
        ResearchFieldCategoriesResult result
    ) {
        return new ResearchFieldCategoriesResponse(
            result.categories().stream()
                .map(CategoryResponse::from)
                .toList()
        );
    }

    public record CategoryResponse(
        Long id,
        String code,
        String name,
        String description,
        int displayOrder,
        Long parentId
    ) {
        private static CategoryResponse from(
            ResearchFieldCategoriesResult.CategoryResult result
        ) {
            return new CategoryResponse(
                result.id(),
                result.code(),
                result.name(),
                result.description(),
                result.displayOrder(),
                result.parentId()
            );
        }
    }
}
