package com.attendance.saas.dto.employee;

import com.attendance.saas.entity.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creates the login account an employee needs before they can check in
 * (Phase 3).
 */
@Schema(name = "CreateEmployeeAccountRequest")
public record CreateEmployeeAccountRequest(

        @NotBlank(message = "Email wajib diisi")
        @Email(message = "Format email tidak valid")
        @Size(max = 150)
        String email,

        @NotBlank(message = "Password wajib diisi")
        @Size(min = 8, max = 72, message = "Password minimal 8 karakter")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password harus mengandung huruf besar, huruf kecil, dan angka")
        String password,

        @NotNull(message = "Role wajib diisi")
        @Schema(description = "Hanya EMPLOYEE, SUPERVISOR, atau HR", example = "EMPLOYEE")
        UserRole role
) {
}
