package com.rentbook.auth;

import com.rentbook.config.RentbookProperties;
import com.rentbook.user.MeResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Writes a session as JSON (access token) plus an httpOnly cookie (refresh token). */
@Component
public class SessionCookies {

    public static final String COOKIE_NAME = "rb_refresh";
    static final String COOKIE_PATH = "/api/v1/auth";

    private final RentbookProperties properties;
    private final Clock clock;

    SessionCookies(RentbookProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public record SessionResponse(String accessToken, Instant accessTokenExpiresAt, MeResponse user) {
    }

    public ResponseEntity<SessionResponse> respond(Sessions.Session session, HttpStatus status) {
        Duration maxAge = Duration.between(clock.instant(), session.refreshExpiresAt());
        ResponseCookie cookie = base(session.refreshToken()).maxAge(maxAge).build();
        SessionResponse body = new SessionResponse(session.accessToken().value(),
                session.accessToken().expiresAt(), MeResponse.of(session.user()));
        return ResponseEntity.status(status).header(HttpHeaders.SET_COOKIE, cookie.toString()).body(body);
    }

    public ResponseCookie cleared() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.auth().cookieSecure())
                .sameSite("Lax")
                .path(COOKIE_PATH);
    }
}
