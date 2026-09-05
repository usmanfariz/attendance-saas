package com.attendance.saas.entity;

import com.attendance.saas.entity.enums.CompanyStatus;
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

import java.time.ZoneId;

/**
 * The tenant root. Every tenant-scoped table carries a {@code company_id}
 * pointing back here.
 */
@Entity
@Table(name = "companies")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Company extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    @Column(nullable = false, length = 150, unique = true)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(nullable = false, length = 64)
    @Builder.Default
    private String timezone = "Asia/Jakarta";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CompanyStatus status = CompanyStatus.ACTIVE;

    /**
     * Business logic must always use the company timezone, never the server one.
     */
    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }

    public boolean isActive() {
        return status != null && status.isActive();
    }
}
