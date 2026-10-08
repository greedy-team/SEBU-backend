package com.sebu.backend.community.profile.controller;

import com.sebu.backend.community.profile.dto.CommunityProfileResponse;
import com.sebu.backend.user.exception.UserNotFoundException;
import com.sebu.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "커뮤니티 프로필",
        description = "사용자의 커뮤니티 활동 프로필 조회 API"
)
@RestController
public class CommunityProfileController {

    @Operation(
            summary = "커뮤니티 프로필 조회 (비활성화)",
            description = "익명 정책에 따라 현재 제공하지 않으며 항상 404를 반환합니다.",
            deprecated = true
    )
    @GetMapping("/api/v1/users/{userId}/community-profile")
    public ApiResponse<CommunityProfileResponse> findProfile(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // Keep the implementation for a future opt-in profile feature, but never expose it here.
        throw new UserNotFoundException();
    }
}
