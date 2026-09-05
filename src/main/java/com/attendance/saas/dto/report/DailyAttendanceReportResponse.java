package com.attendance.saas.dto.report;

import com.attendance.saas.entity.enums.AttendanceStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * One date, one row per employee — including employees who were rostered but
 * never recorded anything, which is how the report surfaces absences.
 */
@Schema(name = "DailyAttendanceReportResponse")
public record DailyAttendanceReportResponse(
        LocalDate date,
        String timezone,
        Summary summary,
        List<Row> rows
) {

    @Schema(name = "DailyAttendanceRow")
    public record Row(
            Long employeeId,
            String employeeCode,
            String employeeName,
            String departmentName,
            String shiftName,
            @JsonFormat(pattern = "HH:mm:ss") @Schema(type = "string") LocalTime checkIn,
            @JsonFormat(pattern = "HH:mm:ss") @Schema(type = "string") LocalTime checkOut,
            AttendanceStatus status,
            int lateMinutes,
            int earlyLeaveMinutes,
            int workMinutes,
            String notes
    ) {
    }

    @Schema(name = "DailyAttendanceSummary")
    public record Summary(
            long totalEmployees,
            long expected,
            long present,
            long late,
            long absent,
            long leave
    ) {
    }
}
