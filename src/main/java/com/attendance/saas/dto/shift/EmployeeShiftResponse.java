package com.attendance.saas.dto.shift;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(name = "EmployeeShiftResponse")
public record EmployeeShiftResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        String employeeName,
        Long shiftId,
        String shiftName,
        @JsonFormat(pattern = "HH:mm") @Schema(type = "string") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") @Schema(type = "string") LocalTime endTime,
        LocalDate shiftDate
) {
}
