package com.sebu.backend.laboratoryreview.controller;

import com.sebu.backend.global.auth.CurrentUserProvider;
import com.sebu.backend.global.response.ApiResponse;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewCreateRequest;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewCreateResponse;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewDeleteResponse;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewListResponse;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewMeResponse;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewSummaryResponse;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewUpdateRequest;
import com.sebu.backend.laboratoryreview.dto.LaboratoryReviewUpdateResponse;
import com.sebu.backend.laboratoryreview.service.LaboratoryReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/laboratories")
@Tag(name = "연구실 리뷰", description = "연구실 리뷰 조회 및 관리 API")
public class LaboratoryReviewController {

    private final LaboratoryReviewService laboratoryReviewService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(
            summary = "연구실 리뷰 등록",
            description = "인증한 사용자가 연구실에 리뷰를 등록합니다."
    )
    @SecurityRequirement(name = "cookieAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "연구실 리뷰 등록 성공",
            useReturnTypeSchema = true
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            ref = "#/components/responses/Conflict"
    )
    @PostMapping("/{laboratoryId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LaboratoryReviewCreateResponse> createReview(
            @PathVariable Long laboratoryId,
            @Valid @RequestBody LaboratoryReviewCreateRequest request
    ) {
        Long userId = currentUserProvider.currentUserId()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "AUTHENTICATION_REQUIRED"
                        )
                );

        LaboratoryReviewCreateResponse response =
                laboratoryReviewService.createReview(
                        laboratoryId,
                        userId,
                        request
                );

        return ApiResponse.success(response);
    }

    @Operation(
            summary = "연구실 리뷰 목록 조회",
            description = "연구실의 리뷰를 페이지 단위로 조회합니다."
    )
    @GetMapping("/{laboratoryId}/reviews")
    public ApiResponse<LaboratoryReviewListResponse> getReviews(
            @PathVariable Long laboratoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long currentUserId = currentUserProvider.currentUserId()
                .orElse(null);

        LaboratoryReviewListResponse response =
                laboratoryReviewService.getReviews(
                        laboratoryId,
                        currentUserId,
                        page,
                        size
                );

        return ApiResponse.success(response);
    }

    @Operation(
            summary = "연구실 리뷰 요약 조회",
            description = "연구실의 리뷰 통계 요약을 조회합니다."
    )
    @GetMapping("/{laboratoryId}/review-summary")
    public ApiResponse<LaboratoryReviewSummaryResponse> getReviewSummary(
            @PathVariable Long laboratoryId
    ) {
        LaboratoryReviewSummaryResponse response =
                laboratoryReviewService.getReviewSummary(
                        laboratoryId
                );

        return ApiResponse.success(response);
    }

    @Operation(
            summary = "내 연구실 리뷰 조회",
            description = "인증한 사용자가 해당 연구실에 작성한 리뷰를 조회합니다."
    )
    @SecurityRequirement(name = "cookieAuth")
    @GetMapping("/{laboratoryId}/reviews/me")
    public ApiResponse<LaboratoryReviewMeResponse> getMyReview(
            @PathVariable Long laboratoryId
    ) {
        Long userId = currentUserProvider.currentUserId()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "AUTHENTICATION_REQUIRED"
                        )
                );

        LaboratoryReviewMeResponse response =
                laboratoryReviewService.getMyReview(
                        laboratoryId,
                        userId
                );

        return ApiResponse.success(response);
    }

    @Operation(
            summary = "연구실 리뷰 수정",
            description = "인증한 사용자가 자신이 작성한 연구실 리뷰를 수정합니다."
    )
    @SecurityRequirement(name = "cookieAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            ref = "#/components/responses/Forbidden"
    )
    @PutMapping("/{laboratoryId}/reviews/{reviewId}")
    public ApiResponse<LaboratoryReviewUpdateResponse> updateReview(
            @PathVariable Long laboratoryId,
            @PathVariable Long reviewId,
            @Valid @RequestBody LaboratoryReviewUpdateRequest request
    ) {
        Long userId = currentUserProvider.currentUserId()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "AUTHENTICATION_REQUIRED"
                        )
                );

        LaboratoryReviewUpdateResponse response =
                laboratoryReviewService.updateReview(
                        laboratoryId,
                        reviewId,
                        userId,
                        request
                );

        return ApiResponse.success(response);
    }

    @Operation(
            summary = "연구실 리뷰 삭제",
            description = "인증한 사용자가 자신이 작성한 연구실 리뷰를 삭제합니다."
    )
    @SecurityRequirement(name = "cookieAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            ref = "#/components/responses/Forbidden"
    )
    @DeleteMapping("/{laboratoryId}/reviews/{reviewId}")
    public ApiResponse<LaboratoryReviewDeleteResponse> deleteReview(
            @PathVariable Long laboratoryId,
            @PathVariable Long reviewId
    ) {
        Long userId = currentUserProvider.currentUserId()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "AUTHENTICATION_REQUIRED"
                        )
                );

        LaboratoryReviewDeleteResponse response =
                laboratoryReviewService.deleteReview(
                        laboratoryId,
                        reviewId,
                        userId
                );

        return ApiResponse.success(response);
    }
}
