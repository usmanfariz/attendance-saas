package com.attendance.saas.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(name = "MonthlyAttendanceReportResponse")
public record MonthlyAttendanceReportResponse(
        int year,
        int month,
        LocalDate startDate,
        LocalDate endDate,
        String timezone,
        int employeeCount,
        List<EmployeeAttendanceSummary> rows
) {
}
