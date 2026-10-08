package com.sebu.backend.mypage.dto;

import com.sebu.backend.user.domain.GpaBand;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ProfileResponse(
        String name,
        String nickname,
        @Schema(description = "학년: 1~4=해당 학년, 5=졸업생", example = "5")
        Short grade,
        Department department,
        GpaBand gpaBand,
        String introduction,
        boolean profileCompleted,
        LocalDateTime profileUpdatedAt
) {
    public record Department(
            String id,
            String name
    ) {
    }
}
