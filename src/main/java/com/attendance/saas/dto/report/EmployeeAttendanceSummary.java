package com.attendance.saas.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Per-employee totals over a date range, aggregated by the database rather than
 * by loading every attendance row.
 *
 * @param recordedDays days that have an attendance row of any status
 */
@Schema(name = "EmployeeAttendanceSummary")
public record EmployeeAttendanceSummary(
        Long employeeId,
        String employeeCode,
        String employeeName,
        String departmentName,
        Long recordedDays,
        Long presentDays,
        Long lateDays,
        Long leaveDays,
        Long absentDays,
        Long totalLateMinutes,
        Long totalEarlyLeaveMinutes,
        Long totalWorkMinutes
) {

    /** Hours worked, rounded to one decimal, for report readability. */
    public double totalWorkHours() {
        return Math.round((totalWorkMinutes == null ? 0 : totalWorkMinutes) / 6.0) / 10.0;
    }
}
