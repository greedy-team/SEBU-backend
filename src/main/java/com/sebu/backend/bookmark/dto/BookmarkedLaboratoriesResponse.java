package com.sebu.backend.bookmark.dto;

import java.time.LocalDateTime;
import java.util.List;

public record BookmarkedLaboratoriesResponse(
        List<BookmarkedLaboratory> items
) {
    public record BookmarkedLaboratory(
            LocalDateTime bookmarkedAt,
            LaboratorySummary laboratory
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

    public record DepartmentSummary(
            String id,
            String name
    ) {
    }

    public record ProfessorSummary(
            String id,
            String name,
            String email
    ) {
    }
}
