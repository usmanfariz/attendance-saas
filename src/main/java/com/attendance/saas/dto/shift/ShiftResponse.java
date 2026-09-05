package com.attendance.saas.dto.shift;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalTime;

@Schema(name = "ShiftResponse")
public record ShiftResponse(
        Long id,
        String name,
        @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "22:00") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "07:00") LocalTime endTime,
        int lateToleranceMinutes,
        int earlyLeaveToleranceMinutes,
        boolean defaultShift,
        @Schema(description = "True bila shift berakhir pada hari berikutnya") boolean crossesMidnight,
        long scheduledMinutes,
        Instant createdAt,
        Instant updatedAt
) {
}
