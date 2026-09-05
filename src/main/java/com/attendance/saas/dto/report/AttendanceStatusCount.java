package com.attendance.saas.dto.report;

import com.attendance.saas.entity.enums.AttendanceStatus;

/**
 * One row of a {@code GROUP BY status} aggregation.
 */
public record AttendanceStatusCount(AttendanceStatus status, Long count) {
}
