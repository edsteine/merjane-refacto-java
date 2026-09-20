package com.nimbleways.springboilerplate.utils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public final class TestClock {
    public static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
    public static final LocalDate TODAY = LocalDate.now(FIXED_CLOCK);

    private TestClock() {
    }
}
