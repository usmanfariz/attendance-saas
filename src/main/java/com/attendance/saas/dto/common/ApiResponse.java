package com.attendance.saas.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Envelope used by every endpoint so clients can rely on one shape.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiResponse")
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        String errorCode,
        List<ApiError> errors,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(data, "Request berhasil diproses");
    }

    public static ApiResponse<Void> message(String message) {
        return new ApiResponse<>(true, message, null, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message, String errorCode, List<ApiError> errors) {
        return new ApiResponse<>(false, message, null, errorCode, errors, Instant.now());
    }

    @Schema(name = "ApiError")
    public record ApiError(String field, String message) {
    }
}
