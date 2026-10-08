package com.sebu.backend.researchfield.category.controller;

import com.sebu.backend.global.response.ApiResponse;
import com.sebu.backend.researchfield.category.dto.ResearchFieldCategoriesResponse;
import com.sebu.backend.researchfield.category.service.ResearchFieldCategoryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/research-field-categories")
@RequiredArgsConstructor
@Tag(name = "연구 분야 카테고리", description = "연구 분야 카테고리 조회 API")
public class ResearchFieldCategoryController {
    private final ResearchFieldCategoryQueryService researchFieldCategoryQueryService;

    @Operation(
            summary = "연구 분야 카테고리 목록 조회",
            description = "사용 가능한 연구 분야 카테고리 전체 목록을 조회합니다."
    )
    @GetMapping
    public ApiResponse<ResearchFieldCategoriesResponse> getAll() {
        return ApiResponse.success(
            ResearchFieldCategoriesResponse.from(
                researchFieldCategoryQueryService.getAll()
            )
        );
    }
}
