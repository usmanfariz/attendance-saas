package com.attendance.saas.entity.enums;

/**
 * Lifecycle shared by leave requests and attendance corrections.
 */
public enum RequestStatus {

    PENDING,
    APPROVED,
    REJECTED,
    /** Withdrawn by the requester before a decision was made. */
    CANCELLED;

    public boolean isPending() {
        return this == PENDING;
    }

    /** A request that still blocks an overlapping one. */
    public boolean isActive() {
        return this == PENDING || this == APPROVED;
    }
}
