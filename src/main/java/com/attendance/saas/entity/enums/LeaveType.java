package com.attendance.saas.entity.enums;

import com.attendance.saas.entity.enums.AttendanceStatus;

public enum LeaveType {

    /** Paid annual leave. */
    CUTI(AttendanceStatus.LEAVE),
    /** Sick leave. */
    SAKIT(AttendanceStatus.SICK),
    /** Personal permission. */
    IZIN(AttendanceStatus.PERMIT);

    private final AttendanceStatus attendanceStatus;

    LeaveType(AttendanceStatus attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }

    /**
     * Status written onto the attendance rows once the request is approved, so
     * an approved absence is never counted as ABSENT.
     */
    public AttendanceStatus attendanceStatus() {
        return attendanceStatus;
    }
}
