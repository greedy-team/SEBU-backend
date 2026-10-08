package com.sebu.backend.community.comment.dto;

import java.time.LocalDateTime;

public record CommentUpdateResponse(
        Long commentId,
        String content,
        LocalDateTime updatedAt
) {
}
