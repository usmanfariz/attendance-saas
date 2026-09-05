package com.attendance.saas.controller;

import com.attendance.saas.dto.common.ApiResponse;
import com.attendance.saas.dto.report.DailyAttendanceReportResponse;
import com.attendance.saas.dto.report.EmployeeAttendanceReportResponse;
import com.attendance.saas.dto.report.MonthlyAttendanceReportResponse;
import com.attendance.saas.entity.enums.AttendanceStatus;
import com.attendance.saas.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Report")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/attendance/daily")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Laporan absensi satu hari untuk seluruh karyawan")
    public ApiResponse<DailyAttendanceReportResponse> daily(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long shiftId,
            @RequestParam(required = false) AttendanceStatus status) {
        return ApiResponse.success(reportService.daily(date, departmentId, shiftId, status));
    }

    @GetMapping("/attendance/monthly")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Rekap absensi bulanan per karyawan")
    public ApiResponse<MonthlyAttendanceReportResponse> monthly(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long employeeId) {
        return ApiResponse.success(reportService.monthly(year, month, departmentId, employeeId));
    }

    @GetMapping("/attendance/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Laporan absensi satu karyawan pada rentang tanggal",
            description = "Tanpa parameter tanggal, rentang default adalah bulan berjalan")
    public ApiResponse<EmployeeAttendanceReportResponse> forEmployee(
            @PathVariable Long employeeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success(reportService.forEmployee(employeeId, startDate, endDate));
    }
}
