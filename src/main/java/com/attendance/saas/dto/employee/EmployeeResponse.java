package com.attendance.saas.dto.employee;

import com.attendance.saas.entity.enums.EmployeeStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

@Schema(name = "EmployeeResponse")
public record EmployeeResponse(
        Long id,
        String employeeCode,
        String name,
        String email,
        String phone,
        Long departmentId,
        String departmentName,
        Long positionId,
        String positionName,
        LocalDate joinDate,
        EmployeeStatus status,
        boolean hasUserAccount,
        Instant createdAt,
        Instant updatedAt
) {
}
