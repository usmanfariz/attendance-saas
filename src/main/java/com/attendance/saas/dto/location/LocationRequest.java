package com.attendance.saas.dto.location;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "LocationRequest")
public record LocationRequest(

        @NotBlank(message = "Nama lokasi wajib diisi")
        @Size(max = 100)
        @Schema(example = "Kantor Pusat Jakarta")
        String name,

        @Size(max = 255)
        String address,

        @NotNull(message = "Latitude wajib diisi")
        @DecimalMin(value = "-90.0", message = "Latitude tidak valid")
        @DecimalMax(value = "90.0", message = "Latitude tidak valid")
        @Schema(example = "-6.200000")
        BigDecimal latitude,

        @NotNull(message = "Longitude wajib diisi")
        @DecimalMin(value = "-180.0", message = "Longitude tidak valid")
        @DecimalMax(value = "180.0", message = "Longitude tidak valid")
        @Schema(example = "106.816666")
        BigDecimal longitude,

        @NotNull(message = "Radius wajib diisi")
        @Min(value = 10, message = "Radius minimal 10 meter")
        @Max(value = 100000, message = "Radius maksimal 100000 meter")
        @Schema(example = "150", description = "Jarak maksimal dari titik kantor, dalam meter")
        Integer radiusMeter,

        @Schema(defaultValue = "true")
        Boolean active
) {
}
