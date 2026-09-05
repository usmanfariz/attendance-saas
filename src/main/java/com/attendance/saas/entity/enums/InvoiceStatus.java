package com.attendance.saas.entity.enums;

public enum InvoiceStatus {

    ISSUED,
    PAID,
    OVERDUE,
    /** Cancelled without payment, e.g. issued in error. */
    VOID;

    public boolean isSettled() {
        return this == PAID || this == VOID;
    }
}
