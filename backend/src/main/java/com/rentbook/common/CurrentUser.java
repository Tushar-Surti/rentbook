package com.rentbook.common;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** Reads the caller's identity from a verified access token. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public static boolean isLandlord(Jwt jwt) {
        return "LANDLORD".equals(jwt.getClaimAsString("role"));
    }
}
