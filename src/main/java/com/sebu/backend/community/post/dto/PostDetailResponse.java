package com.sebu.backend.community.post.dto;

import com.sebu.backend.community.common.dto.CommunityAuthorResponse;
import com.sebu.backend.community.post.domain.CommunityPostCategory;

import java.time.LocalDateTime;
import java.util.List;

public record PostDetailResponse(Post post) {
    public record Post(
            Long id,
            CommunityPostCategory category,
            String title,
            String content,
            CommunityAuthorResponse author,
            List<String> badges,
            long viewCount,
            long likeCount,
            long commentCount,
            boolean liked,
            boolean bookmarked,
            boolean mine,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
