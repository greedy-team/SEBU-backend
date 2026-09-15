package com.sebu.backend.community.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record CommunityAuthorResponse(
        @Schema(description = "익명 정책에 따라 항상 null", nullable = true)
        Long id,
        String nickname,
        CommunityAuthorStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "공개용 단과대 그룹. 미매핑 또는 탈퇴 시 생략", nullable = true)
        String collegeGroup
) {
}
