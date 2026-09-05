package com.attendance.saas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * "Now" is injected rather than read from {@code Instant.now()}, so attendance
 * rules can be tested at any moment of the day without waiting for it.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
