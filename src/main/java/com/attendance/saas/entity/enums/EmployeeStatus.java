package com.attendance.saas.entity.enums;

public enum EmployeeStatus {

    /** Currently employed and expected to record attendance. */
    ACTIVE,
    /** Temporarily not working (long leave, suspension). */
    INACTIVE,
    /** Employment ended; kept for historical attendance records. */
    RESIGNED;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
