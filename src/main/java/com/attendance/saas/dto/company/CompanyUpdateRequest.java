package com.attendance.saas.dto.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CompanyUpdateRequest")
public record CompanyUpdateRequest(

        @NotBlank(message = "Nama perusahaan wajib diisi")
        @Size(max = 150)
        String name,

        @NotBlank(message = "Email perusahaan wajib diisi")
        @Email(message = "Format email tidak valid")
        @Size(max = 150)
        String email,

        @Size(max = 30)
        String phone,

        @Size(max = 255)
        String address,

        @NotBlank(message = "Timezone wajib diisi")
        @Size(max = 64)
        @Schema(example = "Asia/Jakarta")
        String timezone
) {
}
