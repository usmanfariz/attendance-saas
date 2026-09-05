package com.attendance.saas.dto.shift;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

@Schema(name = "ShiftRequest")
public record ShiftRequest(

        @NotBlank(message = "Nama shift wajib diisi")
        @Size(max = 100)
        @Schema(example = "Shift 3 Malam")
        String name,

        @NotNull(message = "Jam mulai wajib diisi")
        @JsonFormat(pattern = "HH:mm")
        @Schema(type = "string", example = "22:00", description = "Waktu lokal perusahaan")
        LocalTime startTime,

        @NotNull(message = "Jam selesai wajib diisi")
        @JsonFormat(pattern = "HH:mm")
        @Schema(type = "string", example = "07:00",
                description = "Jika lebih awal atau sama dengan jam mulai, shift dianggap melewati tengah malam")
        LocalTime endTime,

        @Min(value = 0, message = "Toleransi tidak boleh negatif")
        @Max(value = 720, message = "Toleransi maksimal 720 menit")
        @Schema(example = "15")
        Integer lateToleranceMinutes,

        @Min(value = 0, message = "Toleransi tidak boleh negatif")
        @Max(value = 720, message = "Toleransi maksimal 720 menit")
        @Schema(example = "10")
        Integer earlyLeaveToleranceMinutes,

        @Schema(description = "Shift yang dipakai bila karyawan tidak punya jadwal khusus",
                defaultValue = "false")
        Boolean defaultShift
) {
}
