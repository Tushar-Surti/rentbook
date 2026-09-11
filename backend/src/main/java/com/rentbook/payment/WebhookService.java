package com.rentbook.payment;

import com.rentbook.common.ApiException;
import com.rentbook.common.SecureTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Razorpay's webhook, the only thing that can mark a payment paid. Each delivery is verified against
 * the raw body before it is parsed, claimed once by its event id, then applied. A delivery that fails
 * to apply releases its claim, so Razorpay's retry gets another go.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    public enum Outcome { PROCESSED, DUPLICATE, IGNORED }

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final RazorpayProperties razorpay;
    private final PaymentConfirmation confirmation;
    private final Clock clock;

    WebhookService(JdbcTemplate jdbc, JsonMapper json, RazorpayProperties razorpay, PaymentConfirmation confirmation,
                   Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.razorpay = razorpay;
        this.confirmation = confirmation;
        this.clock = clock;
    }

    public Outcome receive(byte[] body, String signature, String eventIdHeader) {
        String secret = razorpay.webhookSecret();
        if (secret == null || secret.isBlank() || !Signatures.webhookValid(body, signature, secret)) {
            log.warn("Rejected a Razorpay webhook with a bad or missing signature");
            throw ApiException.badRequest("bad_signature", "Signature check failed.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> event = json.readValue(body, Map.class);
        String type = String.valueOf(event.get("event"));
        String eventId = eventIdHeader != null && !eventIdHeader.isBlank()
                ? eventIdHeader : type + ":" + SecureTokens.sha256Hex(new String(body, StandardCharsets.UTF_8));

        int claimed = jdbc.update("""
                insert into webhook_events (id, event_id, event_type, payload, status, received_at)
                values (?, ?, ?, cast(? as jsonb), 'RECEIVED', ?)
                on conflict (event_id) do nothing
                """, UUID.randomUUID(), eventId, type, new String(body, StandardCharsets.UTF_8),
                Timestamp.from(clock.instant()));
        if (claimed == 0) {
            return Outcome.DUPLICATE;
        }
        try {
            boolean applied = apply(type, event);
            jdbc.update("update webhook_events set status = ?, processed_at = ? where event_id = ?",
                    applied ? "PROCESSED" : "IGNORED", Timestamp.from(clock.instant()), eventId);
            return applied ? Outcome.PROCESSED : Outcome.IGNORED;
        } catch (RuntimeException e) {
            jdbc.update("delete from webhook_events where event_id = ?", eventId);
            log.error("Could not apply Razorpay event {} ({}); released for retry", eventId, type, e);
            throw e;
        }
    }

    private boolean apply(String type, Map<String, Object> event) {
        return switch (type) {
            case "order.paid", "payment.captured" -> confirmation.captured(
                    orderId(event), string(event, "payload", "payment", "entity", "id"),
                    number(event, "payload", "payment", "entity", "amount"),
                    string(event, "payload", "payment", "entity", "currency"));
            case "payment.failed" -> confirmation.failed(
                    orderId(event), string(event, "payload", "payment", "entity", "id"),
                    string(event, "payload", "payment", "entity", "error_description"));
            case "transfer.processed", "transfer.failed", "transfer.reversed" -> confirmation.transfer(
                    string(event, "payload", "transfer", "entity", "source"),
                    string(event, "payload", "transfer", "entity", "id"),
                    type.substring("transfer.".length()));
            default -> false;
        };
    }

    private static String orderId(Map<String, Object> event) {
        String fromPayment = string(event, "payload", "payment", "entity", "order_id");
        return fromPayment != null ? fromPayment : string(event, "payload", "order", "entity", "id");
    }

    private static String string(Map<String, Object> root, String... path) {
        Object value = at(root, path);
        return value == null ? null : String.valueOf(value);
    }

    private static long number(Map<String, Object> root, String... path) {
        return at(root, path) instanceof Number number ? number.longValue() : -1;
    }

    @SuppressWarnings("unchecked")
    private static Object at(Map<String, Object> root, String... path) {
        Object current = root;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(key);
        }
        return current;
    }
}
