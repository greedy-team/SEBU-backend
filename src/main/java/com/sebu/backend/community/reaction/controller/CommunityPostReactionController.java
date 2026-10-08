package com.sebu.backend.community.reaction.controller;

import com.sebu.backend.auth.exception.AccessTokenInvalidException;
import com.sebu.backend.community.reaction.dto.PostBookmarkResponse;
import com.sebu.backend.community.reaction.dto.PostLikeResponse;
import com.sebu.backend.community.reaction.service.CommunityPostReactionService;
import com.sebu.backend.global.auth.CurrentUserProvider;
import com.sebu.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "커뮤니티 반응",
        description = "커뮤니티 게시글 좋아요 및 북마크 관리 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/posts/{postId}")
public class CommunityPostReactionController {
    private final CommunityPostReactionService reactionService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(
            summary = "게시글 좋아요 추가",
            description = "로그인한 사용자가 게시글에 좋아요를 추가합니다."
    )
    @SecurityRequirement(name = "cookieAuth")
    @PutMapping("/likes")
    public ApiResponse<PostLikeResponse> like(@PathVariable Long postId) {
        return ApiResponse.success(reactionService.like(requireCurrentUser(), postId));
    }

    @Operation(
            summary = "게시글 좋아요 취소",
            description = "로그인한 사용자가 게시글의 좋아요를 취소합니다."
    )
    @SecurityRequirement(name = "cookieAuth")
    @DeleteMapping("/likes")
    public ApiResponse<PostLikeResponse> unlike(@PathVariable Long postId) {
        return ApiResponse.success(reactionService.unlike(requireCurrentUser(), postId));
    }

    @Operation(
            summary = "게시글 북마크 추가",
            description = "로그인한 사용자가 게시글을 북마크에 추가합니다."
    )
    @SecurityRequirement(name = "cookieAuth")
    @PutMapping("/bookmarks")
    public ApiResponse<PostBookmarkResponse> bookmark(@PathVariable Long postId) {
        return ApiResponse.success(reactionService.bookmark(requireCurrentUser(), postId));
    }

    @Operation(
            summary = "게시글 북마크 삭제",
            description = "로그인한 사용자가 게시글 북마크를 삭제합니다."
    )
    @SecurityRequirement(name = "cookieAuth")
    @DeleteMapping("/bookmarks")
    public ApiResponse<PostBookmarkResponse> unbookmark(@PathVariable Long postId) {
        return ApiResponse.success(reactionService.unbookmark(requireCurrentUser(), postId));
    }

    private Long requireCurrentUser() {
        return currentUserProvider.currentUserId()
                .orElseThrow(AccessTokenInvalidException::new);
    }
}
