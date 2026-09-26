package com.rentbook.auth;

import com.rentbook.Flows;
import com.rentbook.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        String email = Flows.email("cookie");
        register(email, flows.emailedCode(email))
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
        String code = flows.emailedCode(email);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"Ravi Kumar","email":"%s","password":"%s","code":"%s","role":"TENANT"}"""
                        .formatted(email, Flows.PASSWORD, code)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("LANDLORD"));
        register(email.toUpperCase(), code)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_taken"));
        flows.sendCode(email.toUpperCase())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_taken"));
    }

    @Test
    void anAccountNeedsTheCodeEmailedToThatAddress() throws Exception {
        String email = Flows.email("code");
        register(email, "123456").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("code_expired"));

        String code = flows.emailedCode(email);
        assertThat(code).hasSize(6);
        // A second email within the minute is refused, so the address can't be flooded.
        flows.sendCode(email).andExpect(status().isTooManyRequests());
        // Another address's code is no good here.
        String other = Flows.email("other");
        register(email, flows.emailedCode(other)).andExpect(jsonPath("$.code").value("wrong_code"));

        CLOCK.advance(EmailCodes.TTL);
        register(email, code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("code_expired"));

        String fresh = flows.emailedCode(email);
        register(email, fresh).andExpect(status().isCreated());
        // Used up with the account.
        register(email, fresh).andExpect(status().isConflict());
    }

    @Test
    void fiveWrongCodesUseTheCodeUp() throws Exception {
        String email = Flows.email("guess");
        String code = flows.emailedCode(email);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < EmailCodes.MAX_ATTEMPTS; i++) {
            register(email, wrong).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("wrong_code"));
        }
        register(email, code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("code_expired"));
        Integer accounts = jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email);
        assertThat(accounts).isZero();
    }

    @Test
    void aForgottenPasswordIsResetWithAnEmailedCodeAndOldSessionsEnd() throws Exception {
        String email = Flows.email("forgot");
        Flows.Session before = flows.registerLandlord(email);

        postJson("/api/v1/auth/password/code", "{\"email\":\"%s\"}".formatted(email.toUpperCase()))
                .andExpect(status().isNoContent());
        String code = emailedCode(email);
        resetPassword(email, code.equals("000000") ? "111111" : "000000", "a brand new password")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("wrong_code"));
        resetPassword(email, code, "short").andExpect(status().isBadRequest());

        resetPassword(email, code, "a brand new password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("rb_refresh=")));

        // The old password and every session from before are gone; the code is used up.
        login(email, Flows.PASSWORD).andExpect(status().isUnauthorized());
        login(email, "a brand new password").andExpect(status().isOk());
        flows.refresh(before.refreshToken()).andExpect(status().isUnauthorized());
        resetPassword(email, code, "yet another password").andExpect(jsonPath("$.code").value("code_expired"));
    }

    @Test
    void anUnknownAddressGetsTheSameAnswerAndNoEmail() throws Exception {
        String stranger = Flows.email("stranger");
        postJson("/api/v1/auth/password/code", "{\"email\":\"%s\"}".formatted(stranger)).andExpect(status().isNoContent());
        verify(mail, never()).send(any(SimpleMailMessage.class));
        resetPassword(stranger, "123456", "a brand new password")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("code_expired"));

        // A second request within the minute also looks the same from outside.
        String email = Flows.email("twice");
        flows.registerLandlord(email);
        postJson("/api/v1/auth/password/code", "{\"email\":\"%s\"}".formatted(email)).andExpect(status().isNoContent());
        postJson("/api/v1/auth/password/code", "{\"email\":\"%s\"}".formatted(email)).andExpect(status().isNoContent());
    }

    @Test
    void aSignupCodeCannotResetAPassword() throws Exception {
        String email = Flows.email("mixed");
        flows.registerLandlord(email);
        String other = Flows.email("unused");
        String signupCode = flows.emailedCode(other);
        resetPassword(email, signupCode, "a brand new password").andExpect(jsonPath("$.code").value("code_expired"));
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String path, String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private org.springframework.test.web.servlet.ResultActions resetPassword(String email, String code, String password)
            throws Exception {
        return postJson("/api/v1/auth/password/reset", """
                {"email":"%s","code":"%s","password":"%s"}""".formatted(email, code, password));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(email, password));
    }

    private org.springframework.test.web.servlet.ResultActions register(String email, String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Lata Iyer","email":"%s","password":"%s","code":"%s"}""".formatted(email, Flows.PASSWORD, code)));
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
