package com.attendance.saas.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "ChangePasswordRequest")
public record ChangePasswordRequest(

        @NotBlank(message = "Password lama wajib diisi")
        String currentPassword,

        @NotBlank(message = "Password baru wajib diisi")
        @Size(min = 8, max = 72, message = "Password minimal 8 karakter")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password harus mengandung huruf besar, huruf kecil, dan angka")
        String newPassword
) {
}
