package com.attendance.saas.dto.location;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "LocationResponse")
public record LocationResponse(
        Long id,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        int radiusMeter,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
