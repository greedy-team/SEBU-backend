package com.sebu.backend.community.comment.controller;

import com.sebu.backend.auth.exception.AccessTokenInvalidException;
import com.sebu.backend.community.comment.dto.CommentCreateRequest;
import com.sebu.backend.community.comment.dto.CommentCreateResponse;
import com.sebu.backend.community.comment.dto.CommentDeleteResponse;
import com.sebu.backend.community.comment.dto.CommentListResponse;
import com.sebu.backend.community.comment.dto.CommentUpdateRequest;
import com.sebu.backend.community.comment.dto.CommentUpdateResponse;
import com.sebu.backend.community.comment.service.CommunityCommentCommandService;
import com.sebu.backend.community.comment.service.CommunityCommentQueryService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "커뮤니티 댓글",
        description = "커뮤니티 게시글 댓글 조회 및 관리 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/posts/{postId}/comments")
public class CommunityCommentController {
    private final CommunityCommentCommandService commandService;
    private final CommunityCommentQueryService queryService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(
            summary = "댓글 목록 조회",
            description = "게시글 ID와 페이지 정보로 댓글 목록을 조회합니다."
    )
    @GetMapping
    public ApiResponse<CommentListResponse> findComments(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(queryService.findComments(postId, page, size));
    }

    @Operation(
            summary = "댓글 작성",
            description = "로그인한 사용자가 게시글에 새 댓글을 작성합니다."
    )
    @SecurityRequirement(name = "cookieAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "댓글 작성 성공",
            useReturnTypeSchema = true
    )
    @PostMapping
    public ResponseEntity<ApiResponse<CommentCreateResponse>> create(
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(commandService.create(
                        requireCurrentUser(),
                        postId,
                        request
                )));
    }

    @Operation(
            summary = "댓글 수정",
            description = "로그인한 사용자가 자신이 작성한 댓글을 수정합니다."
    )
    @SecurityRequirement(name = "cookieAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            ref = "#/components/responses/Forbidden"
    )
    @PatchMapping("/{commentId}")
    public ApiResponse<CommentUpdateResponse> update(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request
    ) {
        return ApiResponse.success(commandService.update(
                requireCurrentUser(),
                postId,
                commentId,
                request
        ));
    }

    @Operation(
            summary = "댓글 삭제",
            description = "로그인한 사용자가 자신이 작성한 댓글을 삭제합니다."
    )
    @SecurityRequirement(name = "cookieAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            ref = "#/components/responses/Forbidden"
    )
    @DeleteMapping("/{commentId}")
    public ApiResponse<CommentDeleteResponse> delete(
            @PathVariable Long postId,
            @PathVariable Long commentId
    ) {
        return ApiResponse.success(commandService.delete(
                requireCurrentUser(),
                postId,
                commentId
        ));
    }

    private Long requireCurrentUser() {
        return currentUserProvider.currentUserId()
                .orElseThrow(AccessTokenInvalidException::new);
    }
}
