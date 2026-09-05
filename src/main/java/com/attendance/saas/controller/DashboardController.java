package com.attendance.saas.controller;

import com.attendance.saas.dto.common.ApiResponse;
import com.attendance.saas.dto.dashboard.CompanyDashboardResponse;
import com.attendance.saas.dto.dashboard.EmployeeDashboardResponse;
import com.attendance.saas.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Dashboard perusahaan",
            description = """
                    Ringkasan hari berjalan menurut timezone perusahaan.
                    `absent` dihitung dari karyawan yang terjadwal hari itu dikurangi
                    yang sudah punya catatan absensi, bukan dari total karyawan.
                    """)
    public ApiResponse<CompanyDashboardResponse> companyDashboard(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(dashboardService.companyDashboard(date));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Dashboard karyawan yang sedang login",
            description = "Absensi hari ini, shift berjalan, rekap bulan ini, dan sisa cuti tahunan")
    public ApiResponse<EmployeeDashboardResponse> employeeDashboard() {
        return ApiResponse.success(dashboardService.employeeDashboard());
    }
}
