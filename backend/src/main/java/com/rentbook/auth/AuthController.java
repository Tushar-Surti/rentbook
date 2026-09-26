package com.rentbook.auth;

import com.rentbook.common.ApiException;
import com.rentbook.config.RentbookProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    static final String PHONE_PATTERN = "^\\+?[1-9][0-9]{9,14}$";

    private final AuthService authService;
    private final Sessions sessions;
    private final SessionCookies cookies;
    private final RentbookProperties properties;

    AuthController(AuthService authService, Sessions sessions, SessionCookies cookies, RentbookProperties properties) {
        this.authService = authService;
        this.sessions = sessions;
        this.cookies = cookies;
        this.properties = properties;
    }

    record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = PHONE_PATTERN, message = "Enter a mobile number with country code") String phone,
            @NotBlank @Size(min = 8, max = 72, message = "Use 8 to 72 characters") String password,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Enter the 6-digit code from the email") String code) {
    }

    record CodeRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    record PasswordResetRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Enter the 6-digit code from the email") String code,
            @NotBlank @Size(min = 8, max = 72, message = "Use 8 to 72 characters") String password) {
    }

    record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    @PostMapping("/register/code")
    ResponseEntity<Void> sendCode(@Valid @RequestBody CodeRequest body) {
        authService.sendSignupCode(body.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    ResponseEntity<SessionCookies.SessionResponse> register(@Valid @RequestBody RegisterRequest body) {
        Sessions.Session session = authService.registerLandlord(
                body.fullName(), body.email(), body.phone(), body.password(), body.code());
        return cookies.respond(session, HttpStatus.CREATED);
    }

    /** Always 204, whether or not the address has an account. */
    @PostMapping("/password/code")
    ResponseEntity<Void> sendPasswordResetCode(@Valid @RequestBody CodeRequest body) {
        authService.sendPasswordResetCode(body.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset")
    ResponseEntity<SessionCookies.SessionResponse> resetPassword(@Valid @RequestBody PasswordResetRequest body) {
        return cookies.respond(authService.resetPassword(body.email(), body.code(), body.password()), HttpStatus.OK);
    }

    @PostMapping("/login")
    ResponseEntity<SessionCookies.SessionResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        Sessions.Session session = authService.login(body.email(), body.password(), request.getRemoteAddr());
        return cookies.respond(session, HttpStatus.OK);
    }

    @PostMapping("/refresh")
    ResponseEntity<SessionCookies.SessionResponse> refresh(
            @CookieValue(name = SessionCookies.COOKIE_NAME, required = false) String refreshToken,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin) {
        requireAllowedOrigin(origin);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw ApiException.unauthorized("session_expired", "Your session has ended. Sign in again.");
        }
        return cookies.respond(sessions.rotate(refreshToken), HttpStatus.OK);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @CookieValue(name = SessionCookies.COOKIE_NAME, required = false) String refreshToken,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin) {
        requireAllowedOrigin(origin);
        if (refreshToken != null && !refreshToken.isBlank()) {
            sessions.end(refreshToken);
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.cleared().toString()).build();
    }

    /** Cookie-authenticated endpoints refuse cross-site browser requests (CSRF). Non-browser clients send no Origin. */
    private void requireAllowedOrigin(String origin) {
        if (origin != null && !properties.cors().allowedOrigins().contains(origin)) {
            throw ApiException.unauthorized("origin_not_allowed", "Request origin is not allowed.");
        }
    }
}
