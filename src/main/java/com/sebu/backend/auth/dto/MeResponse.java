package com.sebu.backend.auth.dto;

import com.sebu.backend.auth.service.CurrentUserService;
import io.swagger.v3.oas.annotations.media.Schema;

public record MeResponse(
    Long id,
    String nickname,
    String studentId,
    String name,
    @Schema(description = "학년: 1~4=해당 학년, 5=졸업생. 미선택 시 null", example = "5", nullable = true)
    Short grade,
    DepartmentResponse department,
    boolean profileCompleted
) {
    public static MeResponse from(CurrentUserService.CurrentUser user) {
        var department = user.department();
        return new MeResponse(
            user.id(),
            user.nickname(),
            user.studentId(),
            user.name(),
            user.grade(),
            department == null ? null : new DepartmentResponse(
                department.id(), department.name()
            ),
            user.profileCompleted()
        );
    }

    public record DepartmentResponse(Long id, String name) {
    }
}
