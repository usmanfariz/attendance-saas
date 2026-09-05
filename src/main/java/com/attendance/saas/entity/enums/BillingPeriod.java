package com.attendance.saas.entity.enums;

import java.time.LocalDate;

public enum BillingPeriod {

    MONTHLY,
    YEARLY;

    /** End of the period that starts on {@code start}, inclusive. */
    public LocalDate endOfPeriod(LocalDate start) {
        return switch (this) {
            case MONTHLY -> start.plusMonths(1).minusDays(1);
            case YEARLY -> start.plusYears(1).minusDays(1);
        };
    }

    public LocalDate nextPeriodStart(LocalDate start) {
        return endOfPeriod(start).plusDays(1);
    }
}
