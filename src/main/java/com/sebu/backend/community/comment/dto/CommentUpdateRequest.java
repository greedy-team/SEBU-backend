package com.sebu.backend.community.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentUpdateRequest(
        @NotBlank @Size(max = 500) String content
) {
    public CommentUpdateRequest {
        content = content == null ? null : content.strip();
    }
}
