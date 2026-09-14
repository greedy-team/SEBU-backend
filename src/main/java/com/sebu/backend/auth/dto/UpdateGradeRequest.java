package com.sebu.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateGradeRequest(
    @NotNull
    @Min(1)
    @Max(5)
    @Schema(description = "학년: 1=1학년, 2=2학년, 3=3학년, 4=4학년, 5=졸업생. 사용자가 직접 선택",
        example = "5")
    Integer grade
) {
}
