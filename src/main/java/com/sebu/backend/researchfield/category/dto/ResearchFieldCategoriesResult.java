package com.sebu.backend.researchfield.category.dto;

import com.sebu.backend.researchfield.category.domain.ResearchFieldCategory;

import java.util.List;

public record ResearchFieldCategoriesResult(List<CategoryResult> categories) {
    public static ResearchFieldCategoriesResult from(
        List<ResearchFieldCategory> categories
    ) {
        return new ResearchFieldCategoriesResult(
            categories.stream()
                .map(CategoryResult::from)
                .toList()
        );
    }

    public record CategoryResult(
        Long id,
        String code,
        String name,
        String description,
        int displayOrder,
        Long parentId
    ) {
        private static CategoryResult from(ResearchFieldCategory category) {
            return new CategoryResult(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getDescription(),
                category.getDisplayOrder(),
                category.getParentId()
            );
        }
    }
}
