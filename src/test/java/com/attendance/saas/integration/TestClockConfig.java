package com.attendance.saas.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the application's system clock with one the tests can move.
 * Declared as {@link MutableClock} so it satisfies injection points asking for
 * either {@code Clock} or {@code MutableClock}.
 */
@TestConfiguration
public class TestClockConfig {

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock();
    }
}
