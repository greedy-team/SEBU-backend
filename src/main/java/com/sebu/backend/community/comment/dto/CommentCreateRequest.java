package com.sebu.backend.community.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(
        @NotBlank @Size(max = 500) String content
) {
    public CommentCreateRequest {
        content = content == null ? null : content.strip();
    }
}
