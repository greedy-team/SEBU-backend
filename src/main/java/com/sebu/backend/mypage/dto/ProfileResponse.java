package com.sebu.backend.mypage.dto;

import com.sebu.backend.user.domain.AcademicField;
import io.swagger.v3.oas.annotations.media.Schema;
import com.sebu.backend.user.domain.GpaBand;

import java.time.LocalDateTime;

public record ProfileResponse(
        String name,
        String nickname,
        Short grade,
        Department department,
        @Schema(description = "저장된 전공 계열 코드", example = "ENGINEERING",
                requiredMode = Schema.RequiredMode.REQUIRED)
        AcademicField academicField,
        GpaBand gpaBand,
        String introduction,
        boolean profileCompleted,
        LocalDateTime profileUpdatedAt
) {
    @Schema(name = "MyPageProfileDepartment")
    public record Department(
            String id,
            String name
    ) {
    }
}
