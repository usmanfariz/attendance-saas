package com.attendance.saas.dto.employee;

import com.attendance.saas.entity.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmployeeAccountResponse")
public record EmployeeAccountResponse(
        Long userId,
        Long employeeId,
        String email,
        UserRole role
) {
}
