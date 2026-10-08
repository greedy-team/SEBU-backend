package com.sebu.backend.mypage.dto;

import com.sebu.backend.user.domain.GpaBand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest (

    String nickname,

    @NotNull
    @Min(1)
    @Max(5)
    @Schema(description = "학년: 1=1학년, 2=2학년, 3=3학년, 4=4학년, 5=졸업생. 사용자가 직접 선택",
        example = "5")
    Short grade,

    GpaBand gpaBand,

    @NotNull
    @Size(max = 500)
    String introduction
){}
