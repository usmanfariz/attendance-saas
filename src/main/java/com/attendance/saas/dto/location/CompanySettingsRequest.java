package com.attendance.saas.dto.location;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "CompanySettingsRequest")
public record CompanySettingsRequest(

        @NotNull(message = "geofenceEnabled wajib diisi")
        @Schema(description = "Aktifkan validasi lokasi saat check-in", example = "true")
        Boolean geofenceEnabled
) {
}
