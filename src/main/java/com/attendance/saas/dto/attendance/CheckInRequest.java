package com.attendance.saas.dto.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Coordinates are recorded now and enforced against the office geofence in
 * Phase 4; {@code photo} carries a reference (URL or storage key), never the
 * image bytes.
 */
@Schema(name = "CheckInRequest")
public record CheckInRequest(

        @DecimalMin(value = "-90.0", message = "Latitude tidak valid")
        @DecimalMax(value = "90.0", message = "Latitude tidak valid")
        @Schema(example = "-6.200000")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "Longitude tidak valid")
        @DecimalMax(value = "180.0", message = "Longitude tidak valid")
        @Schema(example = "106.816666")
        BigDecimal longitude,

        @Size(max = 500)
        @Schema(example = "https://storage.example.com/checkin/2026-09-05/emp-001.jpg")
        String photo,

        @Size(max = 500)
        String notes
) {
}
