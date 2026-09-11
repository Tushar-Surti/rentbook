package com.rentbook.auth;

import com.rentbook.Flows;
import com.rentbook.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIT extends IntegrationTest {

    @Test
    void refreshTokensRotateAndAReplayedOneRevokesTheFamily() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("lata"));
        flows.get(landlord, "/api/v1/me").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("LANDLORD"));

        MvcResult rotated = flows.refresh(landlord.refreshToken()).andExpect(status().isOk()).andReturn();
        String newest = Flows.refreshCookie(rotated);
        assertThat(newest).isNotNull().isNotEqualTo(landlord.refreshToken());

        // Moments later the old cookie is most likely a second tab: refused, but the family survives.
        flows.refresh(landlord.refreshToken()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("refresh_race"));

        // Long after rotation it is a replay: the whole family is revoked, including the newest token.
        CLOCK.advance(Duration.ofMinutes(2));
        flows.refresh(landlord.refreshToken()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("session_expired"));
        flows.refresh(newest).andExpect(status().isUnauthorized());
    }

    @Test
    void theRefreshCookieIsHttpOnlyAndScopedToAuth() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"Lata Iyer","email":"%s","password":"%s"}""".formatted(Flows.email("cookie"), Flows.PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")));
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("logout"));
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("rb_refresh", landlord.refreshToken())))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        flows.refresh(landlord.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void registrationAlwaysCreatesALandlordAndEmailsAreUnique() throws Exception {
        String email = Flows.email("unique");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"Ravi Kumar","email":"%s","password":"%s","role":"TENANT"}""".formatted(email, Flows.PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("LANDLORD"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"Ravi Kumar","email":"%s","password":"%s"}""".formatted(email.toUpperCase(), Flows.PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_taken"));
    }

    @Autowired
    ExpiredSessionCleaner sessionCleaner;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void expiredSessionsAreClearedOvernight() throws Exception {
        Flows.Session landlord = flows.registerLandlord(Flows.email("expired"));
        UUID userId = UUID.fromString(landlord.userId());
        jdbc.update("update refresh_tokens set expires_at = now() - interval '1 day' where user_id = ?", userId);

        assertThat(sessionCleaner.clear()).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where user_id = ?", Integer.class, userId))
                .isZero();
        flows.refresh(landlord.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedWrongPasswordsAreThrottled() throws Exception {
        String email = Flows.email("throttle");
        flows.registerLandlord(email);
        String wrong = "{\"email\":\"" + email + "\",\"password\":\"not the password\"}";
        for (int attempt = 0; attempt < LoginThrottle.MAX_FAILURES; attempt++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("bad_credentials"));
        }
        String right = "{\"email\":\"" + email + "\",\"password\":\"" + Flows.PASSWORD + "\"}";
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(right))
                .andExpect(status().isTooManyRequests());

        CLOCK.advance(LoginThrottle.WINDOW.plusSeconds(1));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(right))
                .andExpect(status().isOk());
    }
}
