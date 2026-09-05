package com.attendance.saas.integration;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Test clock the integration tests move around, so check-in and check-out rules
 * can be exercised at any hour without waiting for it.
 */
public class MutableClock extends Clock {

    private volatile Instant instant = Instant.parse("2026-09-05T01:00:00Z");
    private final ZoneId zone;

    public MutableClock() {
        this(ZoneId.of("UTC"));
    }

    private MutableClock(ZoneId zone) {
        this.zone = zone;
    }

    /** Positions the clock at a wall-clock moment in the given timezone. */
    public void setLocalTime(ZoneId companyZone, LocalDate date, int hour, int minute) {
        this.instant = date.atTime(LocalTime.of(hour, minute)).atZone(companyZone).toInstant();
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(newZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
