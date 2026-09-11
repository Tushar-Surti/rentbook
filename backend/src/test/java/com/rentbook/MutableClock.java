package com.rentbook;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests can move forward, to reach expiry windows without waiting for them. */
public final class MutableClock extends Clock {

    private volatile Instant now = Instant.now();

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(now, zone);
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    public void reset() {
        now = Instant.now();
    }
}
