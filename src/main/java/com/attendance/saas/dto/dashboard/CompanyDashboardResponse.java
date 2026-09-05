package com.attendance.saas.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Today at a glance for the whole tenant.
 *
 * @param expectedToday employees rostered to work today; {@code absent} is
 *                      measured against this, not against the headcount, so
 *                      people with no shift are not counted as missing
 */
@Schema(name = "CompanyDashboardResponse")
public record CompanyDashboardResponse(
        LocalDate date,
        String timezone,
        long totalEmployees,
        long expectedToday,
        long present,
        long late,
        long absent,
        long leave,
        long pendingLeaveRequests,
        long pendingCorrections
) {
}
