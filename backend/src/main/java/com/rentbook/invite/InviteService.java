package com.rentbook.invite;

import com.rentbook.auth.Sessions;
import com.rentbook.common.ApiException;
import com.rentbook.common.SecureTokens;
import com.rentbook.config.RentbookProperties;
import com.rentbook.lease.Lease;
import com.rentbook.lease.LeaseRepository;
import com.rentbook.property.Property;
import com.rentbook.property.PropertyRepository;
import com.rentbook.property.PropertyService;
import com.rentbook.property.Unit;
import com.rentbook.property.UnitRepository;
import com.rentbook.user.Role;
import com.rentbook.user.User;
import com.rentbook.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The only way a tenant account comes to exist: a landlord invites someone to a vacant unit on stated
 * terms, and the invitee accepts. Acceptance creates the tenant (or signs in an existing tenant) and
 * the lease in one transaction.
 */
@Service
public class InviteService {

    private final InviteRepository invites;
    private final PropertyService propertyService;
    private final UnitRepository units;
    private final PropertyRepository properties;
    private final LeaseRepository leases;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Sessions sessions;
    private final ApplicationEventPublisher events;
    private final RentbookProperties rentbook;
    private final Clock clock;

    InviteService(InviteRepository invites, PropertyService propertyService, UnitRepository units,
                  PropertyRepository properties, LeaseRepository leases, UserRepository users,
                  PasswordEncoder passwordEncoder, Sessions sessions, ApplicationEventPublisher events,
                  RentbookProperties rentbook, Clock clock) {
        this.invites = invites;
        this.propertyService = propertyService;
        this.units = units;
        this.properties = properties;
        this.leases = leases;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.events = events;
        this.rentbook = rentbook;
        this.clock = clock;
    }

    public record Terms(String tenantName, String email, String phone, long rentPaise, long depositPaise, int dueDay,
                        LocalDate startsOn, LocalDate endsOn) {
    }

    /** Published inside the transaction; listeners act after commit. The raw token travels only here. */
    public record InviteIssued(UUID inviteId, URI link) {
    }

    public record InviteAccepted(UUID inviteId, UUID leaseId, UUID landlordId, UUID unitId, String tenantName) {
    }

    public record Issued(Invite invite, URI link) {
    }

    public record Preview(String tenantName, String email, String landlordName, String propertyName, String city,
                          Unit.Kind unitKind, String unitLabel, String roomLabel, long rentPaise, long depositPaise,
                          int dueDay, LocalDate startsOn, LocalDate endsOn, Instant expiresAt,
                          boolean existingAccount) {
    }

    @Transactional
    public Issued create(UUID landlordId, UUID unitId, Terms terms) {
        Unit unit = propertyService.leasableUnit(landlordId, unitId);
        Instant now = clock.instant();
        invites.findByUnitIdAndStatus(unit.getId(), Invite.Status.PENDING).ifPresent(existing -> {
            if (existing.isOpen(now)) {
                throw ApiException.conflict("invite_pending",
                        "This unit already has an open invite. Resend or revoke it first.");
            }
            existing.expire();
            invites.flush();
        });
        if (terms.endsOn() != null && !terms.endsOn().isAfter(terms.startsOn())) {
            throw ApiException.badRequest("end_before_start", "The lease must end after it starts.");
        }
        String email = User.normalizeEmail(terms.email());
        users.findByEmail(email).filter(user -> user.getRole() == Role.LANDLORD).ifPresent(user -> {
            throw ApiException.conflict("email_is_landlord", "This email belongs to a landlord account.");
        });
        String token = SecureTokens.generate();
        Invite invite = invites.save(new Invite(landlordId, unit.getId(), terms.tenantName(), email, terms.phone(),
                terms.rentPaise(), terms.depositPaise(), terms.dueDay(), terms.startsOn(), terms.endsOn(),
                SecureTokens.sha256Hex(token), now.plus(rentbook.invites().ttl())));
        return announce(invite, token);
    }

    @Transactional
    public Issued resend(UUID landlordId, UUID inviteId) {
        Invite invite = owned(landlordId, inviteId);
        if (invite.getStatus() != Invite.Status.PENDING) {
            throw ApiException.conflict("invite_closed", "Only a pending invite can be resent.");
        }
        String token = SecureTokens.generate();
        invite.reissue(SecureTokens.sha256Hex(token), clock.instant().plus(rentbook.invites().ttl()));
        return announce(invite, token);
    }

    @Transactional
    public Invite revoke(UUID landlordId, UUID inviteId) {
        Invite invite = owned(landlordId, inviteId);
        if (invite.getStatus() != Invite.Status.PENDING) {
            throw ApiException.conflict("invite_closed", "Only a pending invite can be revoked.");
        }
        invite.revoke();
        return invite;
    }

    @Transactional(readOnly = true)
    public List<Invite> list(UUID landlordId) {
        return invites.findByLandlordIdOrderByCreatedAtDesc(landlordId);
    }

    @Transactional(readOnly = true)
    public Preview preview(String rawToken) {
        Invite invite = invites.findByTokenHash(SecureTokens.sha256Hex(rawToken))
                .orElseThrow(() -> ApiException.notFound("Invite"));
        requireOpen(invite);
        Unit unit = units.findById(invite.getUnitId()).orElseThrow(() -> ApiException.notFound("Invite"));
        Unit room = unit.getParentUnitId() == null ? null : units.findById(unit.getParentUnitId()).orElse(null);
        Property property = properties.findById(unit.getPropertyId()).orElseThrow(() -> ApiException.notFound("Invite"));
        String landlordName = users.findById(invite.getLandlordId()).map(User::getFullName).orElse("");
        return new Preview(invite.getTenantName(), invite.getEmail(), landlordName, property.getName(),
                property.getCity(), unit.getKind(), unit.getLabel(), room == null ? null : room.getLabel(),
                invite.getRentPaise(), invite.getDepositPaise(), invite.getDueDay(), invite.getStartsOn(),
                invite.getEndsOn(), invite.getExpiresAt(), users.existsByEmail(invite.getEmail()));
    }

    /**
     * New invitees choose a password (and may correct their name); an existing tenant proves the account
     * with their password. The email is always the one the landlord invited.
     */
    @Transactional
    public Sessions.Session accept(String rawToken, String fullName, String phone, String password) {
        Invite invite = invites.lockByTokenHash(SecureTokens.sha256Hex(rawToken))
                .orElseThrow(() -> ApiException.notFound("Invite"));
        requireOpen(invite);
        Unit unit = units.findById(invite.getUnitId()).orElseThrow(() -> ApiException.notFound("Invite"));
        if (unit.getStatus() != Unit.Status.VACANT) {
            throw ApiException.conflict("unit_not_vacant", "This unit is no longer available. Ask your landlord.");
        }
        User tenant = users.findByEmail(invite.getEmail())
                .map(existing -> existingTenant(existing, password))
                .orElseGet(() -> newTenant(invite, fullName, phone, password));

        Lease lease = leases.save(new Lease(unit.getId(), invite.getLandlordId(), tenant.getId(), invite.getId(),
                invite.getRentPaise(), invite.getDepositPaise(), invite.getDueDay(), invite.getStartsOn(),
                invite.getEndsOn()));
        unit.markOccupied();
        invite.accept(tenant.getId(), clock.instant());
        events.publishEvent(new InviteAccepted(invite.getId(), lease.getId(), invite.getLandlordId(), unit.getId(),
                tenant.getFullName()));
        return sessions.start(tenant);
    }

    private User existingTenant(User user, String password) {
        if (user.getRole() != Role.TENANT || !user.isActive()) {
            throw ApiException.conflict("email_unavailable", "This email can't accept a tenant invite.");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("bad_credentials", "That password doesn't match your Rentbook account.");
        }
        return user;
    }

    private User newTenant(Invite invite, String fullName, String phone, String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw ApiException.badRequest("weak_password", "Use a password of 8 to 72 characters.");
        }
        String name = fullName == null || fullName.isBlank() ? invite.getTenantName() : fullName;
        String contact = phone == null || phone.isBlank() ? invite.getPhone() : phone;
        return users.save(new User(Role.TENANT, name, invite.getEmail(), contact, passwordEncoder.encode(password)));
    }

    private void requireOpen(Invite invite) {
        switch (invite.effectiveStatus(clock.instant())) {
            case PENDING -> { }
            case ACCEPTED -> throw ApiException.gone("invite_used", "This invite has already been accepted. Sign in instead.");
            case REVOKED -> throw ApiException.gone("invite_revoked", "This invite was withdrawn. Ask your landlord for a new one.");
            case EXPIRED -> throw ApiException.gone("invite_expired", "This invite has expired. Ask your landlord to resend it.");
        }
    }

    private Issued announce(Invite invite, String token) {
        URI link = URI.create(rentbook.appBaseUrl().toString().replaceAll("/+$", "") + "/invite/" + token);
        events.publishEvent(new InviteIssued(invite.getId(), link));
        return new Issued(invite, link);
    }

    private Invite owned(UUID landlordId, UUID inviteId) {
        return invites.findByIdAndLandlordId(inviteId, landlordId).orElseThrow(() -> ApiException.notFound("Invite"));
    }
}
