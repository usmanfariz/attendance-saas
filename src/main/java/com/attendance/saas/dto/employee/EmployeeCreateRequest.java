package com.attendance.saas.dto.employee;

import com.attendance.saas.entity.enums.EmployeeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "EmployeeCreateRequest")
public record EmployeeCreateRequest(

        @NotBlank(message = "Kode karyawan wajib diisi")
        @Size(max = 50)
        @Pattern(
                regexp = "^[A-Za-z0-9._-]+$",
                message = "Kode karyawan hanya boleh berisi huruf, angka, titik, garis bawah, dan tanda hubung")
        @Schema(example = "EMP-001")
        String employeeCode,

        @NotBlank(message = "Nama karyawan wajib diisi")
        @Size(max = 150)
        @Schema(example = "Siti Rahayu")
        String name,

        @Email(message = "Format email tidak valid")
        @Size(max = 150)
        @Schema(example = "siti@sumbermakmur.co.id")
        String email,

        @Size(max = 30)
        @Schema(example = "+628111222333")
        String phone,

        @Schema(description = "Id departemen dalam perusahaan yang sama", example = "1")
        Long departmentId,

        @Schema(description = "Id posisi dalam perusahaan yang sama", example = "1")
        Long positionId,

        @Schema(example = "2026-01-15")
        LocalDate joinDate,

        @Schema(defaultValue = "ACTIVE")
        EmployeeStatus status
) {
}
