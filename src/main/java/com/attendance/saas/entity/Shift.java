package com.attendance.saas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalTime;

/**
 * A working window expressed in the company's wall-clock time.
 *
 * <p>When {@code endTime} is not after {@code startTime} the shift crosses
 * midnight (22:00 → 07:00) and its check-out happens on the following calendar
 * day. See {@link #crossesMidnight()}.
 */
@Entity
@Table(name = "shifts")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Shift extends TenantEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** Grace period after {@code startTime} before a check-in counts as late. */
    @Column(name = "late_tolerance_minutes", nullable = false)
    @Builder.Default
    private int lateToleranceMinutes = 0;

    /** Grace period before {@code endTime} within which leaving is not early. */
    @Column(name = "early_leave_tolerance_minutes", nullable = false)
    @Builder.Default
    private int earlyLeaveToleranceMinutes = 0;

    /** Used when an employee has no explicit roster entry for the date. */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultShift = false;

    /**
     * A shift ending at or before its start time runs into the next day.
     * A 24-hour shift (start == end) is treated the same way.
     */
    public boolean crossesMidnight() {
        return !endTime.isAfter(startTime);
    }

    /** Scheduled length of the shift in minutes. */
    public long scheduledMinutes() {
        long minutes = java.time.Duration.between(startTime, endTime).toMinutes();
        return minutes > 0 ? minutes : minutes + java.time.Duration.ofDays(1).toMinutes();
    }
}
