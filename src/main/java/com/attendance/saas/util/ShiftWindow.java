package com.attendance.saas.util;

import com.attendance.saas.entity.Shift;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * A concrete occurrence of a {@link Shift}: the shift's wall-clock times pinned
 * to one business date in one company timezone.
 *
 * <p>This is where the night-shift rule lives. For a shift running 22:00 →
 * 07:00 with {@code businessDate} = 5 Sep, {@link #start()} is 5 Sep 22:00 and
 * {@link #end()} is <em>6 Sep</em> 07:00 — the window carries the day rollover
 * so no caller has to reason about it.
 *
 * @param businessDate the date the shift starts on, in {@code zone}
 * @param zone         the company timezone; never the server default
 */
public record ShiftWindow(LocalDate businessDate, Shift shift, ZoneId zone) {

    public ZonedDateTime start() {
        return businessDate.atTime(shift.getStartTime()).atZone(zone);
    }

    public ZonedDateTime end() {
        LocalDate endDate = shift.crossesMidnight() ? businessDate.plusDays(1) : businessDate;
        return endDate.atTime(shift.getEndTime()).atZone(zone);
    }

    public Instant startInstant() {
        return start().toInstant();
    }

    public Instant endInstant() {
        return end().toInstant();
    }

    /** Latest instant a check-in is still considered on time. */
    public Instant lateThreshold() {
        return startInstant().plus(Duration.ofMinutes(shift.getLateToleranceMinutes()));
    }

    /** Earliest instant a check-out is not considered leaving early. */
    public Instant earlyLeaveThreshold() {
        return endInstant().minus(Duration.ofMinutes(shift.getEarlyLeaveToleranceMinutes()));
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(startInstant()) && !instant.isAfter(endInstant());
    }

    /**
     * Window in which a check-in is attributed to this occurrence: from
     * {@code openBefore} ahead of the start until the shift ends.
     */
    public boolean acceptsCheckInAt(Instant instant, Duration openBefore) {
        return !instant.isBefore(startInstant().minus(openBefore)) && !instant.isAfter(endInstant());
    }
}
