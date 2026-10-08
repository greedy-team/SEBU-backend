package com.sebu.backend.community.post.dto;

import com.sebu.backend.community.post.domain.CommunityPostCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostUpdateRequest(
        @NotNull CommunityPostCategory category,
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 2000) String content
) {
    public PostUpdateRequest {
        title = strip(title);
        content = strip(content);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
