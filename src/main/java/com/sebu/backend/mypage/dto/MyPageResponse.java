package com.sebu.backend.mypage.dto;

import com.sebu.backend.community.common.dto.CommunityAuthorResponse;
import com.sebu.backend.community.post.domain.CommunityPostCategory;
import com.sebu.backend.user.domain.GpaBand;
import com.sebu.backend.user.domain.AcademicField;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record MyPageResponse(
        Profile profile,
        Summary summary,
        BookmarkedLaboratories bookmarkedLaboratories,
        BookmarkedPosts bookmarkedPosts
) {
    @Schema(name = "MyPageProfile")
    public record Profile(
            String name,
            String nickname,
            @Schema(description = "학년: 1~4=해당 학년, 5=졸업생. 미선택 시 null", example = "5", nullable = true)
            Short grade,
            DepartmentSummary department,
            @Schema(description = "전공 계열 코드. 아직 선택하지 않은 기존 사용자는 null",
                    example = "ENGINEERING", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
            AcademicField academicField,
            GpaBand gpaBand,
            String introduction,
            boolean profileCompleted,
            LocalDateTime profileUpdatedAt
    ) {
    }

    public record Summary(
            long bookmarkedLaboratoryCount,
            long bookmarkedPostCount
    ) {
    }

    public record BookmarkedLaboratories(
            List<BookmarkedLaboratory> items
    ) {
    }

    public record BookmarkedLaboratory(
            LocalDateTime bookmarkedAt,
            LaboratorySummary laboratory
    ) {
    }

    public record BookmarkedPosts(
            List<BookmarkedPost> items
    ) {
    }

    public record BookmarkedPost(
            LocalDateTime bookmarkedAt,
            PostSummary post
    ) {
    }

    @Schema(name = "MyPagePostSummary")
    public record PostSummary(
            Long id,
            CommunityPostCategory category,
            String title,
            CommunityAuthorResponse author,
            long likeCount,
            long commentCount,
            long viewCount,
            LocalDateTime createdAt
    ) {
    }

    public record LaboratorySummary(
            String id,
            String name,
            String websiteUrl,
            CollegeSummary college,
            DepartmentSummary department,
            ProfessorSummary professor,
            List<String> researchFields,
            String recruitmentStatus,
            long bookmarkCount,
            boolean bookmarked
    ) {
    }

    public record CollegeSummary(
            String id,
            String name
    ) {
    }

    @Schema(name = "MyPageDepartmentSummary")
    public record DepartmentSummary(
            String id,
            String name
    ) {
    }

    public record ProfessorSummary(
            String id,
            String name
    ) {
    }
}
