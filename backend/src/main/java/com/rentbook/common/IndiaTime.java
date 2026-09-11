package com.rentbook.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Rent is due on calendar days in India, whatever zone the server runs in. */
public final class IndiaTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private IndiaTime() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }
}
