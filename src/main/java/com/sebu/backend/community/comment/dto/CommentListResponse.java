package com.sebu.backend.community.comment.dto;

import com.sebu.backend.community.common.dto.CommunityAuthorResponse;

import java.time.LocalDateTime;
import java.util.List;

public record CommentListResponse(
        List<CommentItem> comments,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
    public record CommentItem(
            Long id,
            CommunityAuthorResponse author,
            String content,
            boolean mine,
            @io.swagger.v3.oas.annotations.media.Schema(description = "댓글 작성자가 게시글 작성자인지 여부. 조회자와 무관합니다.")
            boolean isPostAuthor,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
