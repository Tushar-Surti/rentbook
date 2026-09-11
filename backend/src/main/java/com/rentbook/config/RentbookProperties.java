package com.rentbook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties("rentbook")
public record RentbookProperties(
        URI appBaseUrl,
        Cors cors,
        Jwt jwt,
        Auth auth,
        Invites invites,
        Mail mail,
        int platformFeeBps) {

    public record Cors(List<String> allowedOrigins) {
    }

    /** {@code secret} is an opaque string of at least 32 bytes used as the HMAC-SHA256 key. */
    public record Jwt(String issuer, String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    /** {@code fixedEmailCode} replaces the random signup code on a developer's machine; never set in production. */
    public record Auth(boolean cookieSecure, String fixedEmailCode) {
    }

    public record Invites(Duration ttl) {
    }

    public record Mail(String from) {
    }
}
