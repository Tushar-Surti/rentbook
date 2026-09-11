package com.rentbook.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

/**
 * Sends a message at most once per dedupe key. The key is claimed with an insert before sending, so
 * two instances (or a retried job) cannot both send it; the outcome is recorded on the same row.
 */
@Service
public class Notifier {

    private static final Logger log = LoggerFactory.getLogger(Notifier.class);

    public enum Channel { EMAIL, SMS }

    /** {@code subject} and {@code body} are for email; {@code sms} is the one-line text version. */
    public record Message(String subject, String body, String sms) {
    }

    private final JdbcTemplate jdbc;
    private final EmailSender email;
    private final SmsSender sms;
    private final Clock clock;

    Notifier(JdbcTemplate jdbc, EmailSender email, SmsSender sms, Clock clock) {
        this.jdbc = jdbc;
        this.email = email;
        this.sms = sms;
        this.clock = clock;
    }

    /** Returns true when this call sent the message; false when it was already sent or sending failed. */
    public boolean send(UUID userId, Channel channel, String template, String dedupeKey, String to, Message message) {
        UUID id = UUID.randomUUID();
        int claimed = jdbc.update("""
                insert into notifications (id, user_id, channel, template, dedupe_key, status, created_at)
                values (?, ?, ?, ?, ?, 'PENDING', ?)
                on conflict (dedupe_key) do nothing
                """, id, userId, channel.name(), template, dedupeKey, Timestamp.from(clock.instant()));
        if (claimed == 0) {
            return false;
        }
        try {
            String providerId = channel == Channel.EMAIL
                    ? email.send(to, message.subject(), message.body())
                    : sms.send(to, message.sms());
            jdbc.update("update notifications set status = 'SENT', provider_id = ?, sent_at = ? where id = ?",
                    providerId, Timestamp.from(clock.instant()), id);
            return true;
        } catch (RuntimeException e) {
            String error = String.valueOf(e.getMessage());
            jdbc.update("update notifications set status = 'FAILED', error = ? where id = ?",
                    error.length() > 500 ? error.substring(0, 500) : error, id);
            log.warn("{} {} to user {} failed", channel, template, userId, e);
            return false;
        }
    }
}
