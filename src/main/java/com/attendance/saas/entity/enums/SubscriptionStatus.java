package com.attendance.saas.entity.enums;

public enum SubscriptionStatus {

    /** Paid plan being evaluated; full entitlements, no invoice yet. */
    TRIAL,
    ACTIVE,
    /** An invoice is overdue; the tenant keeps read access but cannot grow. */
    PAST_DUE,
    /** Cancelled by the tenant or the platform; runs until the period ends. */
    CANCELLED,
    EXPIRED;

    /** Whether the tenant may still create new employees, locations and users. */
    public boolean allowsProvisioning() {
        return this == TRIAL || this == ACTIVE;
    }

    public boolean isUsable() {
        return this != EXPIRED;
    }
}
