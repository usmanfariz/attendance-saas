package com.attendance.saas.dto.location;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CompanySettingsResponse")
public record CompanySettingsResponse(
        boolean geofenceEnabled,
        @Schema(description = "Jumlah lokasi kantor yang aktif") long activeLocationCount
) {
}
