package com.sebu.backend.community.reaction.dto;

public record PostLikeResponse(
        boolean liked,
        long likeCount
) {
}
