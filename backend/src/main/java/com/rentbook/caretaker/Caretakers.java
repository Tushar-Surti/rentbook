package com.rentbook.caretaker;

import com.rentbook.auth.Sessions;
import com.rentbook.common.ApiException;
import com.rentbook.common.SecureTokens;
import com.rentbook.config.RentbookProperties;
import com.rentbook.property.Property;
import com.rentbook.property.PropertyRepository;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A landlord's caretakers: invited by email to chosen properties, joining from the link, and removable at
 * any time. Removal disables the account and ends its sessions at once; every caretaker request also
 * re-checks {@link #context} so a removed caretaker is refused even with a token still in hand.
 */
@Service
public class Caretakers {

    private final CaretakerRepository caretakers;
    private final PropertyRepository properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Sessions sessions;
    private final ApplicationEventPublisher events;
    private final RentbookProperties rentbook;
    private final Clock clock;

    Caretakers(CaretakerRepository caretakers, PropertyRepository properties, UserRepository users,
               PasswordEncoder passwordEncoder, Sessions sessions, ApplicationEventPublisher events,
               RentbookProperties rentbook, Clock clock) {
        this.caretakers = caretakers;
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.events = events;
        this.rentbook = rentbook;
        this.clock = clock;
    }

    public record PropertyRef(UUID id, String name) {
    }

    public record CaretakerView(UUID id, String fullName, String email, String phone, Caretaker.Status status,
                                List<PropertyRef> properties, Instant inviteExpiresAt) {
    }

    public record Issued(CaretakerView caretaker, URI link) {
    }

    public record Preview(String fullName, String email, String landlordName, List<PropertyRef> properties,
                          Instant expiresAt) {
    }

    /** Who a signed-in caretaker works for, and where. */
    public record Context(UUID caretakerId, UUID userId, String fullName, UUID landlordId, String landlordName,
                          Set<UUID> propertyIds) {
    }

    /** Published inside the transaction; the raw token travels only here, to the email. */
    public record CaretakerInvited(String fullName, String email, String landlordName, URI link) {
    }

    @Transactional
    public Issued invite(UUID landlordId, String fullName, String email, String phone, Collection<UUID> propertyIds) {
        String address = User.normalizeEmail(email);
        List<Property> assigned = owned(landlordId, propertyIds);
        if (users.existsByEmail(address)) {
            throw ApiException.conflict("email_has_account",
                    "This email already has a Rentbook account. A caretaker needs an address of their own.");
        }
        boolean alreadyInvited = caretakers.findByLandlordIdAndStatusNotOrderByCreatedAtAsc(landlordId,
                Caretaker.Status.REMOVED).stream().anyMatch(existing -> existing.getEmail().equals(address));
        if (alreadyInvited) {
            throw ApiException.conflict("caretaker_exists", "You've already invited this caretaker. Resend their invite instead.");
        }
        String token = SecureTokens.generate();
        Caretaker caretaker = caretakers.save(new Caretaker(landlordId, fullName, address, blank(phone),
                assigned.stream().map(Property::getId).toList(), SecureTokens.sha256Hex(token), expiry()));
        return announce(caretaker, token);
    }

    @Transactional(readOnly = true)
    public List<CaretakerView> list(UUID landlordId) {
        List<Caretaker> found = caretakers.findByLandlordIdAndStatusNotOrderByCreatedAtAsc(landlordId,
                Caretaker.Status.REMOVED);
        Map<UUID, Property> byId = properties.findAllById(found.stream().flatMap(c -> c.getPropertyIds().stream())
                .distinct().toList()).stream().collect(Collectors.toMap(Property::getId, Function.identity()));
        return found.stream().map(caretaker -> view(caretaker, byId)).toList();
    }

    @Transactional
    public CaretakerView assign(UUID landlordId, UUID caretakerId, Collection<UUID> propertyIds) {
        Caretaker caretaker = own(landlordId, caretakerId);
        List<Property> assigned = owned(landlordId, propertyIds);
        caretaker.assign(assigned.stream().map(Property::getId).toList());
        return view(caretaker, assigned.stream().collect(Collectors.toMap(Property::getId, Function.identity())));
    }

    @Transactional
    public Issued resend(UUID landlordId, UUID caretakerId) {
        Caretaker caretaker = own(landlordId, caretakerId);
        if (caretaker.getStatus() != Caretaker.Status.INVITED) {
            throw ApiException.conflict("already_joined", caretaker.getFullName() + " has already joined.");
        }
        String token = SecureTokens.generate();
        caretaker.reissue(SecureTokens.sha256Hex(token), expiry());
        return announce(caretaker, token);
    }

    /** Takes access away now: the account can't sign in and every session it had is ended. */
    @Transactional
    public void remove(UUID landlordId, UUID caretakerId) {
        Caretaker caretaker = own(landlordId, caretakerId);
        caretaker.remove();
        if (caretaker.getUserId() != null) {
            users.findById(caretaker.getUserId()).ifPresent(user -> {
                user.disable();
                sessions.endAll(user);
            });
        }
    }

    @Transactional(readOnly = true)
    public Preview preview(String token) {
        Caretaker caretaker = openInvite(token);
        String landlordName = users.findById(caretaker.getLandlordId()).map(User::getFullName).orElse("Your landlord");
        List<PropertyRef> assigned = refs(caretaker, byId(caretaker.getPropertyIds()));
        return new Preview(caretaker.getFullName(), caretaker.getEmail(), landlordName, assigned,
                caretaker.getInviteExpiresAt());
    }

    @Transactional
    public Sessions.Session accept(String token, String fullName, String password) {
        Caretaker caretaker = openInvite(token);
        if (users.existsByEmail(caretaker.getEmail())) {
            throw ApiException.conflict("email_has_account",
                    "This email already has a Rentbook account. Ask the landlord to invite a different address.");
        }
        String name = fullName == null || fullName.isBlank() ? caretaker.getFullName() : fullName.strip();
        User user = users.save(new User(Role.CARETAKER, name, caretaker.getEmail(), caretaker.getPhone(),
                passwordEncoder.encode(password)));
        caretaker.accept(user.getId(), name);
        return sessions.start(user);
    }

    /** The signed-in caretaker's employer and properties; refused once they have been removed. */
    @Transactional(readOnly = true)
    public Context context(UUID userId) {
        Caretaker caretaker = caretakers.findByUserId(userId).filter(found -> found.getStatus() == Caretaker.Status.ACTIVE)
                .orElseThrow(() -> ApiException.forbidden("not_a_caretaker",
                        "You no longer have caretaker access. Ask the landlord if this is a mistake."));
        String landlordName = users.findById(caretaker.getLandlordId()).map(User::getFullName).orElse("Your landlord");
        return new Context(caretaker.getId(), userId, caretaker.getFullName(), caretaker.getLandlordId(), landlordName,
                caretaker.getPropertyIds());
    }

    /** Active caretakers' accounts for one property, for notifications. */
    @Transactional(readOnly = true)
    public List<UUID> usersFor(UUID propertyId) {
        return caretakers.findActiveForProperty(propertyId).stream().map(Caretaker::getUserId).toList();
    }

    private Issued announce(Caretaker caretaker, String token) {
        URI link = URI.create(rentbook.appBaseUrl().toString().replaceAll("/+$", "") + "/caretaker-invite/" + token);
        String landlordName = users.findById(caretaker.getLandlordId()).map(User::getFullName).orElse("A landlord");
        events.publishEvent(new CaretakerInvited(caretaker.getFullName(), caretaker.getEmail(), landlordName, link));
        return new Issued(view(caretaker, byId(caretaker.getPropertyIds())), link);
    }

    private Caretaker openInvite(String token) {
        return caretakers.findByTokenHash(SecureTokens.sha256Hex(token))
                .filter(caretaker -> caretaker.isOpenInvite(clock.instant()))
                .orElseThrow(() -> ApiException.gone("invite_unavailable",
                        "This invite has expired or been replaced. Ask the landlord to send a new one."));
    }

    private Caretaker own(UUID landlordId, UUID caretakerId) {
        return caretakers.findByIdAndLandlordId(caretakerId, landlordId)
                .filter(caretaker -> caretaker.getStatus() != Caretaker.Status.REMOVED)
                .orElseThrow(() -> ApiException.notFound("Caretaker"));
    }

    private List<Property> owned(UUID landlordId, Collection<UUID> propertyIds) {
        Set<UUID> wanted = Set.copyOf(propertyIds == null ? List.of() : propertyIds);
        if (wanted.isEmpty()) {
            throw ApiException.badRequest("no_properties", "Choose at least one property for the caretaker to look after.");
        }
        List<Property> found = properties.findAllById(wanted).stream()
                .filter(property -> property.getLandlordId().equals(landlordId)).toList();
        if (found.size() != wanted.size()) {
            throw ApiException.notFound("Property");
        }
        return found;
    }

    private Map<UUID, Property> byId(Collection<UUID> ids) {
        return properties.findAllById(ids).stream().collect(Collectors.toMap(Property::getId, Function.identity()));
    }

    private static CaretakerView view(Caretaker caretaker, Map<UUID, Property> byId) {
        return new CaretakerView(caretaker.getId(), caretaker.getFullName(), caretaker.getEmail(), caretaker.getPhone(),
                caretaker.getStatus(), refs(caretaker, byId), caretaker.getInviteExpiresAt());
    }

    private static List<PropertyRef> refs(Caretaker caretaker, Map<UUID, Property> byId) {
        return caretaker.getPropertyIds().stream().map(byId::get).filter(java.util.Objects::nonNull)
                .map(property -> new PropertyRef(property.getId(), property.getName()))
                .sorted(Comparator.comparing(PropertyRef::name)).toList();
    }

    private Instant expiry() {
        return clock.instant().plus(rentbook.invites().ttl());
    }

    private static String blank(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
