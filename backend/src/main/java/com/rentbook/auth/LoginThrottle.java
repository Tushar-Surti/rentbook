package com.rentbook.auth;

import com.rentbook.common.ApiException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows password guessing: five failures per IP and email within fifteen minutes locks that pair
 * until the window passes. In-memory, which suits the single backend instance this app runs.
 */
@Component
class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Failures> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    void check(String clientIp, String email) {
        Failures entry = failures.get(key(clientIp, email));
        if (entry != null && entry.count() >= MAX_FAILURES && clock.instant().isBefore(entry.windowEnds())) {
            throw ApiException.tooManyRequests("Too many attempts. Try again in a few minutes.");
        }
    }

    void recordFailure(String clientIp, String email) {
        Instant now = clock.instant();
        failures.compute(key(clientIp, email), (key, entry) ->
                entry == null || now.isAfter(entry.windowEnds())
                        ? new Failures(1, now.plus(WINDOW))
                        : new Failures(entry.count() + 1, entry.windowEnds()));
    }

    void recordSuccess(String clientIp, String email) {
        failures.remove(key(clientIp, email));
    }

    private static String key(String clientIp, String email) {
        return clientIp + '|' + email;
    }

    private record Failures(int count, Instant windowEnds) {
    }
}
