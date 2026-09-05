package com.attendance.saas.dto.billing;

import com.attendance.saas.entity.enums.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

@Schema(name = "SubscriptionResponse")
public record SubscriptionResponse(
        Long id,
        Long companyId,
        String companyName,
        String companyCode,
        PlanResponse plan,
        SubscriptionStatus status,
        LocalDate startDate,
        LocalDate currentPeriodStart,
        LocalDate currentPeriodEnd,
        LocalDate trialEndsAt,
        boolean autoRenew,
        Instant cancelledAt,
        Instant createdAt,
        Instant updatedAt
) {
}
