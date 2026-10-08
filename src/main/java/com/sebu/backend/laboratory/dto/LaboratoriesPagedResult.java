package com.sebu.backend.laboratory.dto;

import java.util.List;

public record LaboratoriesPagedResult(
        List<LaboratoriesResult.LaboratoryResult> laboratories,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
}
