package com.rentbook.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Public on purpose: Razorpay calls it directly. The HMAC signature is the authentication. */
@RestController
class RazorpayWebhookController {

    private final WebhookService webhooks;

    RazorpayWebhookController(WebhookService webhooks) {
        this.webhooks = webhooks;
    }

    /** The body is taken as raw bytes: the signature covers them exactly as sent. */
    @PostMapping("/api/v1/webhooks/razorpay")
    ResponseEntity<Map<String, String>> receive(
            @RequestBody byte[] body,
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(name = "x-razorpay-event-id", required = false) String eventId) {
        return ResponseEntity.ok(Map.of("outcome", webhooks.receive(body, signature, eventId).name()));
    }
}
