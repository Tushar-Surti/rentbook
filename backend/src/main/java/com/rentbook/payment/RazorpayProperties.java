package com.rentbook.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * Test-mode keys from the Razorpay dashboard. {@code webhookSecret} is the secret set on the webhook
 * itself, not the API key secret. Payments stay switched off until both keys are present.
 */
@ConfigurationProperties("rentbook.razorpay")
public record RazorpayProperties(String keyId, String keySecret, String webhookSecret, URI baseUrl) {

    public boolean configured() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }
}
