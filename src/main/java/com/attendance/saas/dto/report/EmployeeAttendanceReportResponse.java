package com.attendance.saas.dto.report;

import com.attendance.saas.dto.attendance.AttendanceResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(name = "EmployeeAttendanceReportResponse")
public record EmployeeAttendanceReportResponse(
        Long employeeId,
        String employeeCode,
        String employeeName,
        String departmentName,
        String positionName,
        LocalDate startDate,
        LocalDate endDate,
        String timezone,
        EmployeeAttendanceSummary summary,
        List<AttendanceResponse> records
) {
}
