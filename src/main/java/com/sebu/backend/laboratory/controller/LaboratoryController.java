package com.sebu.backend.laboratory.controller;

import com.sebu.backend.global.response.ApiResponse;
import com.sebu.backend.laboratory.dto.LaboratoriesPagedResponse;
import com.sebu.backend.laboratory.dto.LaboratoriesResponse;
import com.sebu.backend.laboratory.exception.InvalidLaboratoryPageException;
import com.sebu.backend.laboratory.exception.InvalidLaboratorySizeException;
import com.sebu.backend.laboratory.service.LaboratoryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/laboratories")
@RequiredArgsConstructor
@Tag(name = "연구실", description = "연구실 조회 API")
public class LaboratoryController {

    private final LaboratoryQueryService laboratoryQueryService;

    @Operation(
            summary = "연구실 목록 조회",
            description = "sort가 REVIEW_COUNT_DESC이면 리뷰 수 기준 페이지 목록을, 그 외에는 전체 목록을 조회합니다. "
                    + "각 연구실은 연결된 연구 분야의 ID, 이름, 카테고리 ID 목록을 researchFieldDetails로 반환합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "연구실 목록 조회 성공",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(oneOf = {
                            LaboratoryOpenApiSchemas.ListResponse.class,
                            LaboratoryOpenApiSchemas.PagedResponse.class
                    })
            )
    )
    @GetMapping
    public ApiResponse<?> getAll(
            @Parameter(description = "정렬 방식", example = "REVIEW_COUNT_DESC")
            @RequestParam(required = false) String sort,
            @Parameter(description = "페이지 번호(0부터 시작하며 리뷰 수 정렬 시 사용)", example = "0")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "페이지 크기(1~50이며 리뷰 수 정렬 시 사용)", example = "20")
            @RequestParam(required = false) Integer size
    ) {
        if ("REVIEW_COUNT_DESC".equals(sort)) {
            int resolvedPage = page == null ? 0 : page;
            int resolvedSize = size == null ? 20 : size;

            if (resolvedPage < 0) {
                throw new InvalidLaboratoryPageException();
            }

            if (resolvedSize < 1 || resolvedSize > 50) {
                throw new InvalidLaboratorySizeException();
            }

            return ApiResponse.success(
                    LaboratoriesPagedResponse.from(
                            laboratoryQueryService.getAllByReviewCount(
                                    resolvedPage,
                                    resolvedSize
                            )
                    )
            );
        }

        return ApiResponse.success(
                LaboratoriesResponse.from(
                        laboratoryQueryService.getAll()
                )
        );
    }
}
