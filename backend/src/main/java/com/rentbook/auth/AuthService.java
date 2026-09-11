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

    AuthService(UserRepository users, LandlordProfileRepository landlordProfiles, PasswordEncoder passwordEncoder,
                Sessions sessions, LoginThrottle throttle, RentbookProperties properties) {
        this.users = users;
        this.landlordProfiles = landlordProfiles;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.throttle = throttle;
        this.properties = properties;
    }

    @Transactional
    Sessions.Session registerLandlord(String fullName, String email, String phone, String password) {
        String normalized = User.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw ApiException.conflict("email_taken", "An account with this email already exists. Sign in instead.");
        }
        User landlord = users.save(new User(Role.LANDLORD, fullName, normalized, phone, passwordEncoder.encode(password)));
        landlordProfiles.save(new LandlordProfile(landlord, fullName, properties.platformFeeBps()));
        return sessions.start(landlord);
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
