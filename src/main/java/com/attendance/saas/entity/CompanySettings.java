package com.attendance.saas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Per-tenant options. Kept separate from {@link Company} so later phases can
 * add settings without widening the tenant table.
 */
@Entity
@Table(name = "company_settings")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class CompanySettings extends TenantEntity {

    /**
     * When off, check-in coordinates are still recorded but never rejected.
     * Defaults to off so enabling geofencing stays an explicit decision.
     */
    @Column(name = "geofence_enabled", nullable = false)
    @Builder.Default
    private boolean geofenceEnabled = false;

    /** Annual leave allowance the employee dashboard subtracts taken days from. */
    @Column(name = "annual_leave_quota_days", nullable = false)
    @Builder.Default
    private int annualLeaveQuotaDays = 12;
}
