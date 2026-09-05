package com.attendance.saas.dto.employee;

import com.attendance.saas.entity.enums.EmployeeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Full replacement of the mutable employee fields. {@code employeeCode} is
 * immutable once assigned, so it is not part of this payload.
 */
@Schema(name = "EmployeeUpdateRequest")
public record EmployeeUpdateRequest(

        @NotBlank(message = "Nama karyawan wajib diisi")
        @Size(max = 150)
        String name,

        @Email(message = "Format email tidak valid")
        @Size(max = 150)
        String email,

        @Size(max = 30)
        String phone,

        Long departmentId,

        Long positionId,

        LocalDate joinDate,

        @NotNull(message = "Status wajib diisi")
        EmployeeStatus status
) {
}
