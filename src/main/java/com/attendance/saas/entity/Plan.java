package com.attendance.saas.entity;

import com.attendance.saas.entity.enums.BillingPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * A subscription plan in the platform catalogue.
 *
 * <p>Platform-level, not tenant-owned: it extends {@link BaseEntity} rather
 * than {@link TenantEntity} and is managed only by the super admin.
 *
 * <p>A {@code null} limit means unlimited.
 */
@Entity
@Table(name = "plans")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Plan extends BaseEntity {

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "price_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal priceAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "IDR";

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period", nullable = false, length = 20)
    @Builder.Default
    private BillingPeriod billingPeriod = BillingPeriod.MONTHLY;

    @Column(name = "max_employees")
    private Integer maxEmployees;

    @Column(name = "max_locations")
    private Integer maxLocations;

    @Column(name = "max_users")
    private Integer maxUsers;

    /** Whether geofenced check-in is part of the plan. */
    @Column(name = "geofence_included", nullable = false)
    @Builder.Default
    private boolean geofenceIncluded = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    public boolean isFree() {
        return priceAmount == null || priceAmount.signum() == 0;
    }
}
