package com.attendance.saas.dto.correction;

import com.attendance.saas.entity.enums.RequestStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(name = "AttendanceCorrectionResponse")
public record AttendanceCorrectionResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        Long attendanceId,
        LocalDate attendanceDate,
        @JsonFormat(pattern = "HH:mm") @Schema(type = "string") LocalTime requestedCheckIn,
        @JsonFormat(pattern = "HH:mm") @Schema(type = "string") LocalTime requestedCheckOut,
        String reason,
        RequestStatus status,
        Long reviewedById,
        String reviewedByName,
        Instant reviewedAt,
        String reviewNote,
        @Schema(description = "Nilai sebelum koreksi diterapkan, sebagai jejak audit")
        Instant previousCheckIn,
        Instant previousCheckOut,
        Instant createdAt,
        Instant updatedAt
) {
}
