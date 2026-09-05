package com.attendance.saas.dto.leave;

import com.attendance.saas.entity.enums.LeaveType;
import com.attendance.saas.entity.enums.RequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

@Schema(name = "LeaveRequestResponse")
public record LeaveRequestResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        LeaveType leaveType,
        LocalDate startDate,
        LocalDate endDate,
        int totalDays,
        String reason,
        RequestStatus status,
        Long reviewedById,
        String reviewedByName,
        Instant reviewedAt,
        String reviewNote,
        Instant createdAt,
        Instant updatedAt
) {
}
