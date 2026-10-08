package com.sebu.backend.auth.controller;

import com.sebu.backend.auth.dto.MeResponse;
import com.sebu.backend.auth.dto.UpdateGradeRequest;
import com.sebu.backend.auth.service.CurrentUserService;
import com.sebu.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "내 정보", description = "로그인한 사용자의 기본 정보 조회 및 수정 API")
public class MeController {
    private final CurrentUserService currentUserService;

    @Operation(summary = "내 정보 조회", description = "로그인한 사용자의 기본 정보를 조회합니다.")
    @SecurityRequirement(name = "cookieAuth")
    @GetMapping
    public ApiResponse<MeResponse> me() {
        return ApiResponse.success(MeResponse.from(currentUserService.getCurrentUser()));
    }

    @Operation(summary = "내 학년 수정", description = "로그인한 사용자의 학년 정보를 수정합니다. 1~4는 해당 학년, 5는 졸업생입니다.")
    @SecurityRequirement(name = "cookieAuth")
    @PatchMapping("/profile")
    public ApiResponse<MeResponse> updateProfile(@Valid @RequestBody UpdateGradeRequest request) {
        return ApiResponse.success(MeResponse.from(currentUserService.updateGrade(request.grade())));
    }
}
