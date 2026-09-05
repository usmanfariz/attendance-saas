package com.attendance.saas.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest")
public record LoginRequest(

        @NotBlank(message = "Email wajib diisi")
        @Email(message = "Format email tidak valid")
        @Size(max = 150)
        @Schema(example = "admin@company-a.com")
        String email,

        @NotBlank(message = "Password wajib diisi")
        @Size(min = 8, max = 72, message = "Password minimal 8 karakter")
        @Schema(example = "Password123!")
        String password
) {
}
