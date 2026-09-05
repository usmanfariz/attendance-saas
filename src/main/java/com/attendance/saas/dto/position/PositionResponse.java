package com.attendance.saas.dto.position;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "PositionResponse")
public record PositionResponse(
        Long id,
        String name,
        String description,
        long employeeCount,
        Instant createdAt,
        Instant updatedAt
) {
}
