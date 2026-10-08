package com.sebu.backend.community.post.dto;

import java.time.LocalDateTime;

public record PostUpdateResponse(
        Long postId,
        LocalDateTime updatedAt
) {
}
