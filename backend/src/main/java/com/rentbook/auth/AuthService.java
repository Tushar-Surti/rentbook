package com.rentbook.auth;

import com.rentbook.common.ApiException;
import com.rentbook.config.RentbookProperties;
import com.rentbook.user.LandlordProfile;
import com.rentbook.user.LandlordProfileRepository;
import com.rentbook.user.Role;
import com.rentbook.user.User;
import com.rentbook.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Landlord self-registration and password login. Tenants are created only by accepting an invite. */
@Service
public class AuthService {

    private final UserRepository users;
    private final LandlordProfileRepository landlordProfiles;
    private final PasswordEncoder passwordEncoder;
    private final Sessions sessions;
    private final LoginThrottle throttle;
    private final RentbookProperties properties;
    private final EmailCodes emailCodes;

    AuthService(UserRepository users, LandlordProfileRepository landlordProfiles, PasswordEncoder passwordEncoder,
                Sessions sessions, LoginThrottle throttle, RentbookProperties properties, EmailCodes emailCodes) {
        this.users = users;
        this.landlordProfiles = landlordProfiles;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.throttle = throttle;
        this.properties = properties;
        this.emailCodes = emailCodes;
    }

    private void requireUnused(String normalizedEmail) {
        if (users.existsByEmail(normalizedEmail)) {
            throw ApiException.conflict("email_taken", "An account with this email already exists. Sign in instead.");
        }
    }

    /** The first step of registration: prove the inbox before the account exists. */
    void sendSignupCode(String email) {
        String normalized = User.normalizeEmail(email);
        requireUnused(normalized);
        emailCodes.send(normalized, EmailCodes.Purpose.SIGNUP);
    }

    /** Doesn't roll back on {@link ApiException}, so a wrong code still counts as a try. */
    @Transactional(noRollbackFor = ApiException.class)
    Sessions.Session registerLandlord(String fullName, String email, String phone, String password, String code) {
        String normalized = User.normalizeEmail(email);
        requireUnused(normalized);
        emailCodes.consume(normalized, code, EmailCodes.Purpose.SIGNUP);
        User landlord = users.save(new User(Role.LANDLORD, fullName, normalized, phone, passwordEncoder.encode(password)));
        landlordProfiles.save(new LandlordProfile(landlord, fullName, properties.platformFeeBps()));
        return sessions.start(landlord);
    }

    /**
     * Emails a reset code if the address belongs to an active account. Every other case answers the same
     * way, including a second request within the minute, so the endpoint can't tell anyone who has an account.
     */
    void sendPasswordResetCode(String email) {
        String normalized = User.normalizeEmail(email);
        if (users.findByEmail(normalized).filter(User::isActive).isEmpty()) {
            return;
        }
        try {
            emailCodes.send(normalized, EmailCodes.Purpose.RESET);
        } catch (ApiException e) {
            if (e.getStatusCode().value() != 429) {
                throw e;
            }
        }
    }

    /**
     * Sets a new password with the emailed code, ends every existing session (a reset often follows a
     * lost phone or a shared laptop), and signs the user in. Doesn't roll back on {@link ApiException},
     * so a wrong code still counts as a try.
     */
    @Transactional(noRollbackFor = ApiException.class)
    Sessions.Session resetPassword(String email, String code, String newPassword) {
        String normalized = User.normalizeEmail(email);
        User user = users.findByEmail(normalized).filter(User::isActive)
                .orElseThrow(() -> ApiException.badRequest("code_expired", "That code has expired. Send a new one."));
        emailCodes.consume(normalized, code, EmailCodes.Purpose.RESET);
        user.changePassword(passwordEncoder.encode(newPassword));
        sessions.endAll(user);
        return sessions.start(user);
    }

    @Transactional
    Sessions.Session login(String email, String password, String clientIp) {
        String normalized = User.normalizeEmail(email);
        throttle.check(clientIp, normalized);
        User user = users.findByEmail(normalized)
                .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
                .filter(User::isActive)
                .orElse(null);
        if (user == null) {
            throttle.recordFailure(clientIp, normalized);
            throw ApiException.unauthorized("bad_credentials", "That email and password don't match.");
        }
        throttle.recordSuccess(clientIp, normalized);
        return sessions.start(user);
    }
}
