package com.rentbook.auth;

import com.rentbook.common.ApiException;
import com.rentbook.config.RentbookProperties;
import com.rentbook.notification.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Six-digit emailed codes: before a landlord account exists, so every landlord owns the address they sign
 * up with, and to reset a forgotten password. Each address holds at most one code per purpose. Codes are stored as an HMAC keyed with the JWT secret, last ten minutes, allow five wrong tries,
 * and can be re-sent once a minute. A fixed code can stand in for a random one on a developer's machine;
 * the production profile refuses to start with one set.
 */
@Component
class EmailCodes {

    private static final Logger log = LoggerFactory.getLogger(EmailCodes.class);

    static final Duration TTL = Duration.ofMinutes(10);
    static final Duration RESEND_AFTER = Duration.ofMinutes(1);
    static final int MAX_ATTEMPTS = 5;

    enum Purpose {
        SIGNUP("Your code to open your Rentbook account is %s.\n\n"
                + "It works for 10 minutes. If you didn't ask for it, you can ignore this email.\n"),
        RESET("Your code to reset your Rentbook password is %s.\n\n"
                + "It works for 10 minutes. If you didn't ask for it, you can ignore this email; "
                + "your password stays the same.\n");

        private final String body;

        Purpose(String body) {
            this.body = body;
        }
    }

    private final JdbcTemplate jdbc;
    private final EmailSender email;
    private final Clock clock;
    private final byte[] key;
    private final String fixedCode;
    private final SecureRandom random = new SecureRandom();

    EmailCodes(JdbcTemplate jdbc, EmailSender email, Clock clock, RentbookProperties properties, Environment environment) {
        this.jdbc = jdbc;
        this.email = email;
        this.clock = clock;
        this.key = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        this.fixedCode = properties.auth().fixedEmailCode();
        if (usesFixedCode() && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("rentbook.auth.fixed-email-code must not be set in production");
        }
    }

    private boolean usesFixedCode() {
        return fixedCode != null && !fixedCode.isBlank();
    }

    /** Sending happens inside the transaction, so a code that never left is never stored. */
    @Transactional
    void send(String address, Purpose purpose) {
        Instant now = clock.instant();
        List<Timestamp> previous = jdbc.queryForList(
                "select sent_at from email_codes where email = ? and purpose = ? for update",
                Timestamp.class, address, purpose.name());
        if (!previous.isEmpty() && previous.getFirst().toInstant().plus(RESEND_AFTER).isAfter(now)) {
            throw ApiException.tooManyRequests("A code is already on its way. You can ask for another in a minute.");
        }
        String code = usesFixedCode() ? fixedCode : "%06d".formatted(random.nextInt(1_000_000));
        jdbc.update("""
                insert into email_codes (email, purpose, code_hash, attempts, sent_at, expires_at) values (?, ?, ?, 0, ?, ?)
                on conflict (email, purpose) do update
                    set code_hash = excluded.code_hash, attempts = 0, sent_at = excluded.sent_at, expires_at = excluded.expires_at
                """, address, purpose.name(), hash(address, code), Timestamp.from(now), Timestamp.from(now.plus(TTL)));
        try {
            email.send(address, "Your Rentbook code is " + code, purpose.body.formatted(code));
        } catch (RuntimeException e) {
            if (usesFixedCode()) {
                // A developer's machine without a mail catcher: the code is known, so the flow carries on.
                log.warn("{} code email to {} failed; the fixed code still works", purpose, address, e);
                return;
            }
            throw ApiException.serviceUnavailable("email_not_sent",
                    "We couldn't send the code just now. Check the address, or try again in a minute.");
        }
    }

    /**
     * Uses up the code if it matches. The caller's transaction must not roll back on {@link ApiException},
     * so a wrong guess still counts against the five tries.
     */
    void consume(String address, String code, Purpose purpose) {
        List<CodeRow> rows = jdbc.query(
                "select code_hash, attempts, expires_at from email_codes where email = ? and purpose = ? for update",
                (rs, n) -> new CodeRow(rs.getString(1), rs.getInt(2), rs.getTimestamp(3).toInstant()),
                address, purpose.name());
        if (rows.isEmpty() || !rows.getFirst().expiresAt().isAfter(clock.instant())) {
            throw ApiException.badRequest("code_expired", "That code has expired. Send a new one.");
        }
        CodeRow row = rows.getFirst();
        if (row.attempts() >= MAX_ATTEMPTS) {
            throw ApiException.badRequest("code_expired", "Too many wrong tries. Send a new code.");
        }
        boolean matches = MessageDigest.isEqual(
                row.codeHash().getBytes(StandardCharsets.US_ASCII), hash(address, code).getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            jdbc.update("update email_codes set attempts = attempts + 1 where email = ? and purpose = ?",
                    address, purpose.name());
            throw ApiException.badRequest("wrong_code", "That code isn't right. Check the email and try again.");
        }
        jdbc.update("delete from email_codes where email = ? and purpose = ?", address, purpose.name());
    }

    private String hash(String address, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((address + '|' + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private record CodeRow(String codeHash, int attempts, Instant expiresAt) {
    }
}
