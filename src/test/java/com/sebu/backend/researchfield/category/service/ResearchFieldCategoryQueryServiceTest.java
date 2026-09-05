package com.sebu.backend.researchfield.category.service;

import com.sebu.backend.researchfield.category.domain.ResearchFieldCategory;
import com.sebu.backend.researchfield.category.repository.ResearchFieldCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchFieldCategoryQueryServiceTest {
    @Mock
    ResearchFieldCategoryRepository researchFieldCategoryRepository;

    @Mock
    ResearchFieldCategory firstCategory;

    @Mock
    ResearchFieldCategory secondCategory;

    @InjectMocks
    ResearchFieldCategoryQueryService service;

    @Test
    void returnsRepositoryOrderAsCategoryResults() {
        when(researchFieldCategoryRepository.findAllByOrderByDisplayOrderAscIdAsc())
            .thenReturn(List.of(firstCategory, secondCategory));
        mockCategory(
            firstCategory,
            1L,
            "AI_ML",
            "인공지능·기계학습",
            "인공지능과 기계학습",
            1
        );
        mockCategory(
            secondCategory,
            2L,
            "DATA_INFORMATION",
            "데이터과학·정보관리",
            "데이터과학과 정보관리",
            2
        );

        var categories = service.getAll().categories();

        assertThat(categories)
            .extracting(category -> category.code())
            .containsExactly("AI_ML", "DATA_INFORMATION");
        assertThat(categories.getFirst().description())
            .isEqualTo("인공지능과 기계학습");
        assertThat(categories.getFirst().displayOrder()).isEqualTo(1);
    }

    @Test
    void exposesLifeScienceCategoriesAfterExistingCategoriesInRepositoryOrder() {
        ResearchFieldCategory foodCategory = mock(ResearchFieldCategory.class);
        ResearchFieldCategory plantCategory = mock(ResearchFieldCategory.class);
        ResearchFieldCategory molecularCategory = mock(ResearchFieldCategory.class);
        when(researchFieldCategoryRepository.findAllByOrderByDisplayOrderAscIdAsc())
            .thenReturn(List.of(firstCategory, foodCategory, plantCategory, molecularCategory));
        mockCategory(firstCategory, 21L, "CHEMISTRY_MATERIALS", "화학·소재", "화학과 소재", 21);
        mockCategory(foodCategory, 22L, "FOOD_NUTRITION", "식품·영양", "식품과 영양", 22);
        mockCategory(
            plantCategory,
            23L,
            "PLANT_AGRICULTURE",
            "식물·농업생명과학",
            "식물과 농업생명과학",
            23
        );
        mockCategory(
            molecularCategory,
            24L,
            "MOLECULAR_BIOTECH",
            "분자·세포생물학·생명공학",
            "분자·세포생물학과 생명공학",
            24
        );

        assertThat(service.getAll().categories())
            .extracting(category -> category.code(), category -> category.name(),
                category -> category.displayOrder())
            .containsExactly(
                tuple("CHEMISTRY_MATERIALS", "화학·소재", 21),
                tuple("FOOD_NUTRITION", "식품·영양", 22),
                tuple("PLANT_AGRICULTURE", "식물·농업생명과학", 23),
                tuple("MOLECULAR_BIOTECH", "분자·세포생물학·생명공학", 24)
            );
    }

    @Test
    void returnsEmptyResultWhenNoCategoryExists() {
        when(researchFieldCategoryRepository.findAllByOrderByDisplayOrderAscIdAsc())
            .thenReturn(List.of());

        assertThat(service.getAll().categories()).isEmpty();
    }

    private void mockCategory(
        ResearchFieldCategory category,
        Long id,
        String code,
        String name,
        String description,
        int displayOrder
    ) {
        when(category.getId()).thenReturn(id);
        when(category.getCode()).thenReturn(code);
        when(category.getName()).thenReturn(name);
        when(category.getDescription()).thenReturn(description);
        when(category.getDisplayOrder()).thenReturn(displayOrder);
    }
}
