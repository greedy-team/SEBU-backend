package com.sebu.backend.community.post.dto;

import com.sebu.backend.community.common.dto.CommunityAuthorResponse;
import com.sebu.backend.community.post.domain.CommunityPostCategory;

import java.time.LocalDateTime;
import java.util.List;

public record PostListResponse(
        List<PostSummary> posts,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
    public record PostSummary(
            Long id,
            CommunityPostCategory category,
            String title,
            CommunityAuthorResponse author,
            List<String> badges,
            long likeCount,
            long commentCount,
            long viewCount,
            LocalDateTime createdAt
    ) {
    }
}
