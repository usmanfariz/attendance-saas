package com.attendance.saas.dto.billing;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Platform-wide view for the super admin (specification section 5).
 */
@Schema(name = "SystemDashboardResponse")
public record SystemDashboardResponse(
        long totalCompanies,
        long activeCompanies,
        long suspendedCompanies,
        long totalEmployees,
        long totalUsers,
        Map<String, Long> subscriptionsByStatus,
        List<PlanDistribution> subscriptionsByPlan,
        @Schema(description = "Nilai langganan berulang per bulan dari langganan aktif")
        BigDecimal monthlyRecurringRevenue,
        String currency,
        long unpaidInvoices,
        long overdueInvoices,
        @Schema(description = "Total tagihan lunas pada bulan berjalan")
        BigDecimal revenueThisMonth
) {

    @Schema(name = "PlanDistribution")
    public record PlanDistribution(String planCode, long companies) {
    }
}
