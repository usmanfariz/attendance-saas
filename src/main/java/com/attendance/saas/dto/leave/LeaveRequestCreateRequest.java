package com.attendance.saas.dto.leave;

import com.attendance.saas.entity.enums.LeaveType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "LeaveRequestCreateRequest")
public record LeaveRequestCreateRequest(

        @NotNull(message = "Jenis cuti wajib diisi")
        @Schema(example = "CUTI", description = "CUTI, SAKIT, atau IZIN")
        LeaveType leaveType,

        @NotNull(message = "Tanggal mulai wajib diisi")
        @Schema(example = "2026-09-10")
        LocalDate startDate,

        @NotNull(message = "Tanggal selesai wajib diisi")
        @Schema(example = "2026-09-12")
        LocalDate endDate,

        @NotBlank(message = "Alasan wajib diisi")
        @Size(max = 500)
        @Schema(example = "Acara keluarga di luar kota")
        String reason
) {
}
