package com.attendance.saas.dto.position;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "PositionRequest")
public record PositionRequest(

        @NotBlank(message = "Nama posisi wajib diisi")
        @Size(max = 100)
        @Schema(example = "Operator Mesin")
        String name,

        @Size(max = 255)
        String description
) {
}
