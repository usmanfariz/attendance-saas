package com.attendance.saas.entity;

import com.attendance.saas.entity.enums.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The plan a company is on right now — exactly one row per tenant.
 *
 * <p>Changing plan updates this row; the billing history lives in
 * {@link Invoice} and the audit log, so there is never ambiguity about which
 * subscription is in force.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Subscription extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "current_period_start", nullable = false)
    private LocalDate currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private LocalDate currentPeriodEnd;

    @Column(name = "trial_ends_at")
    private LocalDate trialEndsAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "auto_renew", nullable = false)
    @Builder.Default
    private boolean autoRenew = true;

    public boolean allowsProvisioning() {
        return status != null && status.allowsProvisioning();
    }

    public boolean isTrialExpired(LocalDate today) {
        return status == SubscriptionStatus.TRIAL
                && trialEndsAt != null
                && today.isAfter(trialEndsAt);
    }

    public boolean isPeriodOver(LocalDate today) {
        return today.isAfter(currentPeriodEnd);
    }
}
