package com.attendance.saas.dto.department;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "DepartmentResponse")
public record DepartmentResponse(
        Long id,
        String name,
        String description,
        long employeeCount,
        Instant createdAt,
        Instant updatedAt
) {
}
