package com.attendance.saas.dto.department;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "DepartmentRequest")
public record DepartmentRequest(

        @NotBlank(message = "Nama departemen wajib diisi")
        @Size(max = 100)
        @Schema(example = "Produksi")
        String name,

        @Size(max = 255)
        @Schema(example = "Departemen yang menangani lini produksi")
        String description
) {
}
