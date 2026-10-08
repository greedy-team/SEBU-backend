package com.sebu.backend.laboratory.controller;

import com.sebu.backend.global.response.ApiResponse;
import com.sebu.backend.laboratory.dto.LaboratoriesPagedResponse;
import com.sebu.backend.laboratory.dto.LaboratoriesResponse;
import io.swagger.v3.oas.annotations.media.Schema;

final class LaboratoryOpenApiSchemas {

    private LaboratoryOpenApiSchemas() {
    }

    @Schema(name = "LaboratoriesListApiResponse")
    record ListResponse(
            @Schema(example = "true") boolean success,
            LaboratoriesResponse data,
            @Schema(nullable = true) ApiResponse.ApiError error
    ) {
    }

    @Schema(name = "LaboratoriesPagedApiResponse")
    record PagedResponse(
            @Schema(example = "true") boolean success,
            LaboratoriesPagedResponse data,
            @Schema(nullable = true) ApiResponse.ApiError error
    ) {
    }
}
