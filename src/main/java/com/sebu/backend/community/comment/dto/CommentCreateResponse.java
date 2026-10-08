package com.sebu.backend.community.comment.dto;

public record CommentCreateResponse(
        CommentListResponse.CommentItem comment,
        long commentCount
) {
}
