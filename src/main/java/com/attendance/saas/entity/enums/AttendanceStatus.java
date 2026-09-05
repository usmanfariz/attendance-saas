package com.attendance.saas.entity.enums;

public enum AttendanceStatus {

    /** Checked in within the shift's late tolerance. */
    PRESENT,
    /** Checked in after the shift start plus its tolerance. */
    LATE,
    /** No check-in recorded for a working day. */
    ABSENT,
    /** Covered by an approved leave request (Phase 4). */
    LEAVE,
    SICK,
    PERMIT,
    /** Non-working day. */
    HOLIDAY;

    /** True when the employee physically worked, i.e. attendance was recorded. */
    public boolean isWorked() {
        return this == PRESENT || this == LATE;
    }
}
