package com.attendance.saas.dto.shift;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Assigns one shift to one employee over a date range. A single day is a range
 * whose start and end are equal.
 */
@Schema(name = "AssignShiftRequest")
public record AssignShiftRequest(

        @NotNull(message = "Employee id wajib diisi")
        Long employeeId,

        @NotNull(message = "Shift id wajib diisi")
        Long shiftId,

        @NotNull(message = "Tanggal mulai wajib diisi")
        @Schema(example = "2026-09-01")
        LocalDate startDate,

        @NotNull(message = "Tanggal selesai wajib diisi")
        @Schema(example = "2026-09-07")
        LocalDate endDate,

        @Schema(description = "Timpa jadwal yang sudah ada pada rentang tersebut", defaultValue = "false")
        Boolean overwriteExisting
) {
}
