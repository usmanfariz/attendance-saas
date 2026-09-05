package com.attendance.saas.dto.attendance;

import com.attendance.saas.entity.enums.AttendanceStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * @param attendanceDate business date the shift started on, in company time
 * @param checkInLocal   check-in rendered in the company timezone, so clients
 *                       do not have to convert UTC themselves
 */
@Schema(name = "AttendanceResponse")
public record AttendanceResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        Long shiftId,
        String shiftName,
        @JsonFormat(pattern = "HH:mm") @Schema(type = "string") LocalTime shiftStartTime,
        @JsonFormat(pattern = "HH:mm") @Schema(type = "string") LocalTime shiftEndTime,
        LocalDate attendanceDate,
        Instant checkIn,
        Instant checkOut,
        @JsonFormat(pattern = "HH:mm:ss") @Schema(type = "string") LocalTime checkInLocal,
        @JsonFormat(pattern = "HH:mm:ss") @Schema(type = "string") LocalTime checkOutLocal,
        String timezone,
        AttendanceStatus status,
        int lateMinutes,
        int earlyLeaveMinutes,
        int workMinutes,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
