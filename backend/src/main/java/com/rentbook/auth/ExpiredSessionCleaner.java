package com.rentbook.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;

/**
 * A refresh token past its expiry can never be used again, so each night they go. A rotated token only
 * ever points at the newer one that replaced it, which expires later, so one delete never strands a
 * live token's reference.
 */
@Component
class ExpiredSessionCleaner {

    private static final Logger log = LoggerFactory.getLogger(ExpiredSessionCleaner.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    ExpiredSessionCleaner(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(cron = "0 15 4 * * *", zone = "Asia/Kolkata")
    public int clear() {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("delete from email_codes where expires_at < ?", now);
        int gone = jdbc.update("delete from refresh_tokens where expires_at < ?", now);
        if (gone > 0) {
            log.info("Cleared {} expired sessions", gone);
        }
        return gone;
    }
}
