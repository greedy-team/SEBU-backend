package com.sebu.backend.mypage.dto;

import com.sebu.backend.user.domain.AcademicField;
import com.sebu.backend.user.domain.GpaBand;

import java.time.LocalDateTime;

public record ProfileResponse(
        String name,
        String nickname,
        Short grade,
        Department department,
        AcademicField academicField,
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
