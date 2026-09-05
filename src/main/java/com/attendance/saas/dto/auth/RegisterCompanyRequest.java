package com.attendance.saas.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public self-service sign-up: creates the tenant plus its first
 * {@code COMPANY_ADMIN} account in one transaction.
 */
@Schema(name = "RegisterCompanyRequest")
public record RegisterCompanyRequest(

        @NotBlank(message = "Nama perusahaan wajib diisi")
        @Size(max = 150)
        @Schema(example = "PT Sumber Makmur")
        String companyName,

        @NotBlank(message = "Kode perusahaan wajib diisi")
        @Size(min = 3, max = 50)
        @Pattern(
                regexp = "^[A-Za-z0-9-]+$",
                message = "Kode perusahaan hanya boleh berisi huruf, angka, dan tanda hubung")
        @Schema(example = "sumber-makmur")
        String companyCode,

        @NotBlank(message = "Email perusahaan wajib diisi")
        @Email(message = "Format email perusahaan tidak valid")
        @Size(max = 150)
        @Schema(example = "info@sumbermakmur.co.id")
        String companyEmail,

        @Size(max = 30)
        @Schema(example = "+628123456789")
        String phone,

        @Size(max = 255)
        String address,

        @Size(max = 64)
        @Schema(example = "Asia/Jakarta", defaultValue = "Asia/Jakarta")
        String timezone,

        @NotBlank(message = "Nama admin wajib diisi")
        @Size(max = 150)
        @Schema(example = "Budi Santoso")
        String adminName,

        @NotBlank(message = "Email admin wajib diisi")
        @Email(message = "Format email admin tidak valid")
        @Size(max = 150)
        @Schema(example = "budi@sumbermakmur.co.id")
        String adminEmail,

        @NotBlank(message = "Password admin wajib diisi")
        @Size(min = 8, max = 72, message = "Password minimal 8 karakter")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password harus mengandung huruf besar, huruf kecil, dan angka")
        @Schema(example = "Password123!")
        String adminPassword
) {
}
