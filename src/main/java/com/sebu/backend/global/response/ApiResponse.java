package com.sebu.backend.global.response;

import java.util.List;

public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> failure(
            String code,
            String message
    ) {
        return new ApiResponse<>(
                false,
                null,
                new ApiError(
                        code,
                        message,
                        List.of(),
                        null
                )
        );
    }

    public static <T> ApiResponse<T> failure(
            String code,
            String message,
            List<FieldError> fieldErrors,
            String traceId
    ) {
        return new ApiResponse<>(
                false,
                null,
                new ApiError(
                        code,
                        message,
                        fieldErrors,
                        traceId
                )
        );
    }

    public record ApiError(
            String code,
            String message,
            List<FieldError> fieldErrors,
            String traceId
    ) {
    }

    public record FieldError(
            String field,
            String reason,
            String message
    ) {
    }
}
