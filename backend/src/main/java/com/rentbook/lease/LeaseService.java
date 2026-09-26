package com.rentbook.lease;

import com.rentbook.common.ApiException;
import com.rentbook.common.IndiaTime;
import com.rentbook.property.Property;
import com.rentbook.property.PropertyRepository;
import com.rentbook.property.Unit;
import com.rentbook.property.UnitRepository;
import com.rentbook.user.Role;
import com.rentbook.user.User;
import com.rentbook.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Both parties read the same lease. A landlord sees leases on their units, a tenant sees their own,
 * and anything else is "not found".
 */
@Service
public class LeaseService {

    private final LeaseRepository leases;
    private final UnitRepository units;
    private final PropertyRepository properties;
    private final UserRepository users;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    LeaseService(LeaseRepository leases, UnitRepository units, PropertyRepository properties, UserRepository users,
                 Clock clock, ApplicationEventPublisher events) {
        this.leases = leases;
        this.units = units;
        this.properties = properties;
        this.users = users;
        this.clock = clock;
        this.events = events;
    }

    /** The landlord set the tenant's last day: ended now, or on notice until then. The tenant hears after commit. */
    public record LeaseEndSet(LeaseView lease) {
    }

    public record UnitRef(UUID id, Unit.Kind kind, String label, String roomLabel) {
    }

    public record PropertyRef(UUID id, String name, String city) {
    }

    public record PersonRef(UUID id, String fullName, String email, String phone) {
    }

    public record LeaseView(UUID id, Lease.Status status, long rentPaise, long depositPaise, int dueDay,
                            LocalDate startsOn, LocalDate endsOn, UnitRef unit, PropertyRef property,
                            PersonRef landlord, PersonRef tenant) {
    }

    @Transactional(readOnly = true)
    public List<LeaseView> visibleTo(UUID userId, Role role) {
        List<Lease> found = role == Role.LANDLORD
                ? leases.findByLandlordIdOrderByStartsOnDesc(userId)
                : leases.findByTenantIdOrderByStartsOnDesc(userId);
        return views(found);
    }

    @Transactional(readOnly = true)
    public LeaseView view(UUID leaseId, UUID userId, Role role) {
        return views(List.of(require(leaseId, userId, role))).getFirst();
    }

    /** The lease if the caller is one of its two parties. */
    @Transactional(readOnly = true)
    public Lease require(UUID leaseId, UUID userId, Role role) {
        return (role == Role.LANDLORD
                ? leases.findByIdAndLandlordId(leaseId, userId)
                : leases.findByIdAndTenantId(leaseId, userId))
                .orElseThrow(() -> ApiException.notFound("Lease"));
    }

    @Transactional(readOnly = true)
    public boolean isParty(UUID leaseId, UUID userId) {
        return leases.existsByIdAndLandlordId(leaseId, userId) || leases.existsByIdAndTenantId(leaseId, userId);
    }

    @Transactional
    public LeaseView end(UUID leaseId, UUID landlordId, LocalDate endsOn) {
        Lease lease = leases.findByIdAndLandlordId(leaseId, landlordId).orElseThrow(() -> ApiException.notFound("Lease"));
        if (!lease.isLive()) {
            throw ApiException.conflict("lease_ended", "This lease has already ended.");
        }
        if (endsOn.isBefore(lease.getStartsOn())) {
            throw ApiException.badRequest("end_before_start", "The end date can't be before the lease started.");
        }
        lease.end(endsOn, IndiaTime.today(clock));
        if (lease.getStatus() == Lease.Status.ENDED) {
            units.findById(lease.getUnitId()).ifPresent(Unit::markVacant);
        }
        LeaseView view = views(List.of(lease)).getFirst();
        events.publishEvent(new LeaseEndSet(view));
        return view;
    }

    /** Leases on notice whose end date has arrived become ended, and their units vacant. */
    @Transactional
    public int endLeasesDue(LocalDate today) {
        List<Lease> due = leases.findByStatusAndEndsOnLessThanEqual(Lease.Status.NOTICE, today);
        for (Lease lease : due) {
            lease.end(lease.getEndsOn(), today);
            units.findById(lease.getUnitId()).ifPresent(Unit::markVacant);
        }
        return due.size();
    }

    private List<LeaseView> views(List<Lease> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<UUID, Unit> unitsById = byId(units.findAllById(ids(found, Lease::getUnitId)), Unit::getId);
        Map<UUID, Unit> roomsById = byId(units.findAllById(
                unitsById.values().stream().map(Unit::getParentUnitId).filter(Objects::nonNull).toList()), Unit::getId);
        Map<UUID, Property> propertiesById = byId(properties.findAllById(
                unitsById.values().stream().map(Unit::getPropertyId).distinct().toList()), Property::getId);
        Map<UUID, User> usersById = byId(users.findAllById(found.stream()
                .flatMap(lease -> Stream.of(lease.getLandlordId(), lease.getTenantId())).distinct().toList()), User::getId);

        return found.stream().map(lease -> {
            Unit unit = unitsById.get(lease.getUnitId());
            Unit room = unit.getParentUnitId() == null ? null : roomsById.get(unit.getParentUnitId());
            Property property = propertiesById.get(unit.getPropertyId());
            return new LeaseView(lease.getId(), lease.getStatus(), lease.getRentPaise(), lease.getDepositPaise(),
                    lease.getDueDay(), lease.getStartsOn(), lease.getEndsOn(),
                    new UnitRef(unit.getId(), unit.getKind(), unit.getLabel(), room == null ? null : room.getLabel()),
                    new PropertyRef(property.getId(), property.getName(), property.getCity()),
                    person(usersById.get(lease.getLandlordId())), person(usersById.get(lease.getTenantId())));
        }).toList();
    }

    private static PersonRef person(User user) {
        return new PersonRef(user.getId(), user.getFullName(), user.getEmail(), user.getPhone());
    }

    private static List<UUID> ids(Collection<Lease> found, Function<Lease, UUID> getter) {
        return found.stream().map(getter).distinct().toList();
    }

    private static <T> Map<UUID, T> byId(Collection<T> items, Function<T, UUID> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
