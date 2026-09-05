package com.attendance.saas.dto.billing;

import com.attendance.saas.entity.enums.BillingPeriod;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "PlanResponse")
public record PlanResponse(
        Long id,
        String code,
        String name,
        String description,
        BigDecimal priceAmount,
        String currency,
        BillingPeriod billingPeriod,
        @Schema(description = "Null berarti tanpa batas") Integer maxEmployees,
        @Schema(description = "Null berarti tanpa batas") Integer maxLocations,
        @Schema(description = "Null berarti tanpa batas") Integer maxUsers,
        boolean geofenceIncluded,
        boolean active,
        int sortOrder,
        @Schema(description = "Jumlah perusahaan yang sedang memakai plan ini")
        Long subscriberCount,
        Instant createdAt,
        Instant updatedAt
) {
}
