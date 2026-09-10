package com.sebu.backend.community.post.controller;

import com.sebu.backend.auth.exception.AccessTokenInvalidException;
import com.sebu.backend.community.post.domain.CommunityPostCategory;
import com.sebu.backend.community.post.domain.CommunityPostSort;
import com.sebu.backend.community.post.dto.PostCreateRequest;
import com.sebu.backend.community.post.dto.PostCreateResponse;
import com.sebu.backend.community.post.dto.PostDeleteResponse;
import com.sebu.backend.community.post.dto.PostDetailResponse;
import com.sebu.backend.community.post.dto.PostListResponse;
import com.sebu.backend.community.post.dto.PostUpdateRequest;
import com.sebu.backend.community.post.dto.PostUpdateResponse;
import com.sebu.backend.community.post.service.CommunityPostCommandService;
import com.sebu.backend.community.post.service.CommunityPostQueryService;
import com.sebu.backend.global.auth.CurrentUserProvider;
import com.sebu.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "커뮤니티 게시글",
        description = "커뮤니티 게시글 조회 및 관리 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/posts")
public class CommunityPostController {
    private final CommunityPostCommandService commandService;
    private final CommunityPostQueryService queryService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(
            summary = "게시글 목록 조회",
            description = "검색어, 카테고리, 정렬 조건과 페이지 정보로 게시글 목록을 조회합니다."
    )
    @GetMapping
    public ApiResponse<PostListResponse> findPosts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CommunityPostCategory category,
            @RequestParam(defaultValue = "LATEST") CommunityPostSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(queryService.findPosts(keyword, category, sort, page, size));
    }

    @Operation(
            summary = "게시글 상세 조회",
            description = "게시글 ID로 게시글 상세 정보를 조회합니다."
    )
    @GetMapping("/{postId}")
    public ApiResponse<PostDetailResponse> findDetail(@PathVariable Long postId) {
        return ApiResponse.success(queryService.findDetail(postId));
    }

    @Operation(
            summary = "게시글 작성",
            description = "로그인한 사용자가 새 게시글을 작성합니다."
    )
    @SecurityRequirement(name = "cookieAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "게시글 작성 성공",
            useReturnTypeSchema = true
    )
    @PostMapping
    public ResponseEntity<ApiResponse<PostCreateResponse>> create(
            @Valid @RequestBody PostCreateRequest request
    ) {
        Long userId = requireCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(commandService.create(userId, request)));
    }

    @Operation(
            summary = "게시글 수정",
            description = "로그인한 사용자가 자신이 작성한 게시글을 수정합니다."
    )
    @SecurityRequirement(name = "cookieAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            ref = "#/components/responses/Forbidden"
    )
    @PutMapping("/{postId}")
    public ApiResponse<PostUpdateResponse> update(
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request
    ) {
        return ApiResponse.success(commandService.update(requireCurrentUser(), postId, request));
    }

    @Operation(
            summary = "게시글 삭제",
            description = "로그인한 사용자가 자신이 작성한 게시글을 삭제합니다."
    )
    @SecurityRequirement(name = "cookieAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            ref = "#/components/responses/Forbidden"
    )
    @DeleteMapping("/{postId}")
    public ApiResponse<PostDeleteResponse> delete(@PathVariable Long postId) {
        return ApiResponse.success(commandService.delete(requireCurrentUser(), postId));
    }

    private Long requireCurrentUser() {
        return currentUserProvider.currentUserId()
                .orElseThrow(AccessTokenInvalidException::new);
    }
}
