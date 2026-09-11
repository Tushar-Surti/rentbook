package com.rentbook.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Razorpay's HMAC-SHA256 signatures, compared in constant time. */
final class Signatures {

    private Signatures() {
    }

    /** The checkout handler's signature: HMAC of "order_id|payment_id" with the API key secret. */
    static boolean checkoutValid(String orderId, String paymentId, String signature, String keySecret) {
        return matches(hmacHex((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8), keySecret), signature);
    }

    /** A webhook's X-Razorpay-Signature: HMAC of the raw, unparsed body with the webhook secret. */
    static boolean webhookValid(byte[] rawBody, String signature, String webhookSecret) {
        return matches(hmacHex(rawBody, webhookSecret), signature);
    }

    static String hmacHex(byte[] message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available", e);
        }
    }

    private static boolean matches(String expectedHex, String givenHex) {
        if (givenHex == null || givenHex.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.US_ASCII),
                givenHex.strip().toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }
}
