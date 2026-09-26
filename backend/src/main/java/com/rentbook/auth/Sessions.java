package com.rentbook.auth;

import com.rentbook.common.ApiException;
import com.rentbook.common.SecureTokens;
import com.rentbook.config.RentbookProperties;
import com.rentbook.user.User;
import com.rentbook.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Starts sessions and rotates refresh tokens. Presenting a token that was already rotated means it
 * leaked or was replayed, so the whole family is revoked; the one exception is a second browser tab
 * refreshing within a few seconds of the first, which gets a retryable error instead.
 */
@Service
public class Sessions {

    static final Duration ROTATION_GRACE = Duration.ofSeconds(30);

    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final TokenService tokenService;
    private final RentbookProperties properties;
    private final Clock clock;

    Sessions(RefreshTokenRepository refreshTokens, UserRepository users, TokenService tokenService,
             RentbookProperties properties, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.tokenService = tokenService;
        this.properties = properties;
        this.clock = clock;
    }

    public record Session(User user, TokenService.AccessToken accessToken, String refreshToken,
                          Instant refreshExpiresAt) {
    }

    @Transactional
    public Session start(User user) {
        return issue(user, UUID.randomUUID(), clock.instant()).session();
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Session rotate(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHash(SecureTokens.sha256Hex(rawRefreshToken))
                .orElseThrow(Sessions::invalid);
        if (current.wasRotated()) {
            if (current.rotatedWithin(ROTATION_GRACE, now)) {
                throw ApiException.unauthorized("refresh_race", "Session was refreshed in another tab. Retry.");
            }
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw invalid();
        }
        if (!current.isUsable(now)) {
            throw invalid();
        }
        User user = users.findById(current.getUserId()).filter(User::isActive).orElseThrow(Sessions::invalid);
        Issued next = issue(user, current.getFamilyId(), now);
        current.rotateTo(next.token(), now);
        return next.session();
    }

    @Transactional
    public void end(String rawRefreshToken) {
        refreshTokens.findByTokenHash(SecureTokens.sha256Hex(rawRefreshToken))
                .ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    /** Signs the user out on every device. */
    @Transactional
    public void endAll(User user) {
        refreshTokens.revokeAllForUser(user.getId(), clock.instant());
    }

    private Issued issue(User user, UUID familyId, Instant now) {
        String raw = SecureTokens.generate();
        Instant expiresAt = now.plus(properties.jwt().refreshTokenTtl());
        RefreshToken token = refreshTokens.save(
                new RefreshToken(user.getId(), SecureTokens.sha256Hex(raw), familyId, now, expiresAt));
        return new Issued(token, new Session(user, tokenService.issue(user), raw, expiresAt));
    }

    private static ApiException invalid() {
        return ApiException.unauthorized("session_expired", "Your session has ended. Sign in again.");
    }

    private record Issued(RefreshToken token, Session session) {
    }
}
