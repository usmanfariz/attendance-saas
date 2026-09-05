package com.attendance.saas.entity;

import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Base class for every tenant-owned entity.
 *
 * <p>The {@code company} association is mandatory, so a row can never exist
 * outside a tenant. Repositories still scope their queries by company id
 * explicitly — this class only guarantees the column is there.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@MappedSuperclass
public abstract class TenantEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    public Long getCompanyId() {
        return company == null ? null : company.getId();
    }

    public boolean belongsTo(Long companyId) {
        return companyId != null && companyId.equals(getCompanyId());
    }
}
