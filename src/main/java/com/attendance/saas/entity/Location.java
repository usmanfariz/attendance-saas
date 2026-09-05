package com.attendance.saas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * An office the company accepts check-ins from, as a circle of
 * {@code radiusMeter} around a coordinate.
 */
@Entity
@Table(name = "locations")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Location extends TenantEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String address;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "radius_meter", nullable = false)
    @Builder.Default
    private int radiusMeter = 100;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
