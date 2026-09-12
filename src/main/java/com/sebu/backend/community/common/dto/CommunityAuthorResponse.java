package com.sebu.backend.community.common.dto;

public record CommunityAuthorResponse(
        Long id,
        String nickname,
        CommunityAuthorStatus status
) {
}
