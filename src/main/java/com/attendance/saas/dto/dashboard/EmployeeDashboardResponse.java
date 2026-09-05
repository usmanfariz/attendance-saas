package com.attendance.saas.dto.dashboard;

import com.attendance.saas.dto.attendance.AttendanceResponse;
import com.attendance.saas.dto.shift.ShiftResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * The employee's own view: what is happening today, how the month is going, and
 * how much annual leave is left.
 */
@Schema(name = "EmployeeDashboardResponse")
public record EmployeeDashboardResponse(
        Long employeeId,
        String employeeCode,
        String employeeName,
        LocalDate date,
        String timezone,
        @Schema(description = "Absensi hari ini, null bila belum check-in")
        AttendanceResponse todayAttendance,
        @Schema(description = "Shift terjadwal hari ini, null bila tidak ada")
        ShiftResponse currentShift,
        MonthlyAttendanceSummary thisMonth,
        LeaveBalance leaveBalance
) {

    @Schema(name = "MonthlyAttendanceSummary")
    public record MonthlyAttendanceSummary(
            LocalDate monthStart,
            LocalDate monthEnd,
            long presentDays,
            long lateDays,
            long leaveDays,
            long absentDays,
            long totalLateMinutes,
            long totalWorkMinutes
    ) {
    }

    /**
     * @param quotaDays     annual allowance from company settings
     * @param usedDays      approved CUTI days taken this calendar year
     * @param remainingDays quota minus used, never negative
     */
    @Schema(name = "LeaveBalance")
    public record LeaveBalance(
            int year,
            int quotaDays,
            int usedDays,
            int remainingDays,
            int pendingDays
    ) {
    }
}
