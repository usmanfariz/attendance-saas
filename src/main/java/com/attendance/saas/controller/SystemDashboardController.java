package com.attendance.saas.controller;

import com.attendance.saas.dto.billing.SystemDashboardResponse;
import com.attendance.saas.dto.common.ApiResponse;
import com.attendance.saas.service.SystemDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "System Admin")
public class SystemDashboardController {

    private final SystemDashboardService systemDashboardService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Dashboard sistem",
            description = "Jumlah tenant, karyawan, sebaran langganan, dan ringkasan pendapatan")
    public ApiResponse<SystemDashboardResponse> systemDashboard() {
        return ApiResponse.success(systemDashboardService.systemDashboard());
    }
}
