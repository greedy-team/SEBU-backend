package com.sebu.backend.mypage.controller;

import com.sebu.backend.global.response.ApiResponse;
import com.sebu.backend.mypage.dto.MyPageResponse;
import com.sebu.backend.mypage.dto.ProfileResponse;
import io.swagger.v3.oas.annotations.media.Schema;

final class MyPageOpenApiSchemas {
    private MyPageOpenApiSchemas() { }

    @Schema(name = "MyPageApiResponse")
    record PageResponse(boolean success, MyPageResponse data, ApiResponse.ApiError error) { }

    @Schema(name = "MyPageProfileApiResponse")
    record SavedProfileResponse(boolean success, ProfileResponse data, ApiResponse.ApiError error) { }
}
