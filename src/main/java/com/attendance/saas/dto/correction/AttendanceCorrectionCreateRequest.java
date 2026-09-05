package com.attendance.saas.dto.correction;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Times are wall-clock in the company timezone. At least one of
 * {@code checkIn} / {@code checkOut} must be present.
 */
@Schema(name = "AttendanceCorrectionCreateRequest")
public record AttendanceCorrectionCreateRequest(

        @NotNull(message = "Tanggal absensi wajib diisi")
        @Schema(example = "2026-09-05")
        LocalDate attendanceDate,

        @JsonFormat(pattern = "HH:mm")
        @Schema(type = "string", example = "08:00")
        LocalTime checkIn,

        @JsonFormat(pattern = "HH:mm")
        @Schema(type = "string", example = "17:00")
        LocalTime checkOut,

        @NotBlank(message = "Alasan wajib diisi")
        @Size(max = 500)
        @Schema(example = "Lupa melakukan check-in")
        String reason
) {
}
