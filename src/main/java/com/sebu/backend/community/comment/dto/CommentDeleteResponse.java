package com.sebu.backend.community.comment.dto;

public record CommentDeleteResponse(
        Long postId,
        Long commentId,
        long commentCount
) {
}
