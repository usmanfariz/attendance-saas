package com.attendance.saas.service;

import com.attendance.saas.entity.enums.AttendanceStatus;
import com.attendance.saas.util.ShiftWindow;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure arithmetic over a {@link ShiftWindow}: lateness, early leave and worked
 * minutes. Holds no state and touches no database, so every rule in section 24
 * of the specification is unit-testable in isolation.
 *
 * <p>Tolerance is a grace period, not a discount: once a check-in exceeds
 * {@code lateToleranceMinutes}, lateness is counted in full from the shift
 * start. Arriving 20 minutes late under a 15-minute tolerance is 20 minutes
 * late, not 5.
 */
@Component
public class AttendanceCalculator {

    /**
     * Minutes late, or 0 while still inside the tolerance.
     * Rule: {@code actual_check_in > shift_start + tolerance} means late.
     */
    public int lateMinutes(ShiftWindow window, Instant checkIn) {
        if (!checkIn.isAfter(window.lateThreshold())) {
            return 0;
        }
        return toMinutes(Duration.between(window.startInstant(), checkIn));
    }

    /**
     * Minutes left early, or 0 while still inside the tolerance.
     * Rule: {@code actual_check_out < shift_end - tolerance} means early leave.
     */
    public int earlyLeaveMinutes(ShiftWindow window, Instant checkOut) {
        if (!checkOut.isBefore(window.earlyLeaveThreshold())) {
            return 0;
        }
        return toMinutes(Duration.between(checkOut, window.endInstant()));
    }

    /** Elapsed minutes between check-in and check-out; breaks are not deducted. */
    public int workMinutes(Instant checkIn, Instant checkOut) {
        if (checkIn == null || checkOut == null || checkOut.isBefore(checkIn)) {
            return 0;
        }
        return toMinutes(Duration.between(checkIn, checkOut));
    }

    public AttendanceStatus statusFor(int lateMinutes) {
        return lateMinutes > 0 ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
    }

    private int toMinutes(Duration duration) {
        return Math.max(0, Math.toIntExact(duration.toMinutes()));
    }
}
