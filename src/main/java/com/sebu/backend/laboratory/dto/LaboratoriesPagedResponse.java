package com.sebu.backend.laboratory.dto;

import java.util.List;

public record LaboratoriesPagedResponse(
        List<LaboratoriesResponse.LaboratoryResponse> laboratories,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {

    public static LaboratoriesPagedResponse from(
            LaboratoriesPagedResult result
    ) {
        List<LaboratoriesResponse.LaboratoryResponse> laboratories =
                result.laboratories()
                        .stream()
                        .map(LaboratoriesResponse.LaboratoryResponse::from)
                        .toList();

        return new LaboratoriesPagedResponse(
                laboratories,
                result.page(),
                result.size(),
                result.totalElements(),
                result.hasNext()
        );
    }
}
