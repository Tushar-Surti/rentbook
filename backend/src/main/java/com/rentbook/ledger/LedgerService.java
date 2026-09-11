package com.rentbook.ledger;

import com.rentbook.common.ApiException;
import com.rentbook.common.IndiaTime;
import com.rentbook.invite.InviteService;
import com.rentbook.lease.Lease;
import com.rentbook.lease.LeaseRepository;
import com.rentbook.lease.LeaseService;
import com.rentbook.user.Role;
import com.rentbook.user.User;
import com.rentbook.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The one ledger both parties read. Rent charges are generated month by month; the landlord adds
 * one-off charges and may waive any unpaid one. Payments settle charges.
 */
@Service
public class LedgerService {

    /** How far ahead of its due date a rent charge appears. */
    static final int LEAD_DAYS = 10;

    private final ChargeRepository charges;
    private final LeaseRepository leases;
    private final LeaseService leaseService;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    LedgerService(ChargeRepository charges, LeaseRepository leases, LeaseService leaseService, UserRepository users,
                  ApplicationEventPublisher events, Clock clock) {
        this.charges = charges;
        this.leases = leases;
        this.leaseService = leaseService;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /** UPCOMING is not due yet, DUE falls due today, OVERDUE is past its date. Only a verified payment makes PAID. */
    public enum EntryStatus { UPCOMING, DUE, OVERDUE, PAID, WAIVED }

    public record Entry(UUID id, Charge.Kind kind, String description, LocalDate periodMonth, LocalDate dueOn,
                        long amountPaise, EntryStatus status, Instant paidAt, Instant waivedAt) {
    }

    public record Ledger(UUID leaseId, long outstandingPaise, long overduePaise, List<Entry> entries) {
    }

    /**
     * Published inside the transaction; the live channel tells both parties once it commits, including
     * who made the change ({@code actor} is null for rent the system generated).
     */
    public record LedgerChanged(UUID leaseId, String type, String description, long amountPaise, String actor) {
    }

    /** Runs inside the invite-accept transaction, so a lease never exists without its opening charges. */
    @EventListener
    void onMoveIn(InviteService.InviteAccepted accepted) {
        leases.findById(accepted.leaseId()).ifPresent(this::openLease);
    }

    /** The deposit, for a new move-in, and whatever rent is already due. */
    @Transactional
    public void openLease(Lease lease) {
        LocalDate today = IndiaTime.today(clock);
        // A tenant who moved in before joining Rentbook settled their deposit outside it.
        if (lease.getDepositPaise() > 0 && !lease.getStartsOn().isBefore(today)
                && !charges.existsByLeaseIdAndKind(lease.getId(), Charge.Kind.DEPOSIT)) {
            charges.save(Charge.deposit(lease));
        }
        syncRent(lease, today);
    }

    /**
     * Creates every rent charge that should exist by {@code today}: one per month from the lease start,
     * up to {@link #LEAD_DAYS} ahead, never for a month starting after the lease ends, and never for a
     * due date before the lease was recorded here (that rent was settled outside Rentbook).
     */
    @Transactional
    public int syncRent(Lease lease, LocalDate today) {
        if (!lease.isLive()) {
            return 0;
        }
        LocalDate recordedOn = lease.getCreatedAt() == null
                ? today : LocalDate.ofInstant(lease.getCreatedAt(), IndiaTime.ZONE);
        LocalDate horizon = today.plusDays(LEAD_DAYS);
        int created = 0;
        for (YearMonth period = YearMonth.from(lease.getStartsOn()); ; period = period.plusMonths(1)) {
            LocalDate dueOn = dueDate(lease, period);
            if (dueOn.isAfter(horizon)
                    || (lease.getEndsOn() != null && !period.atDay(1).isBefore(lease.getEndsOn()))) {
                break;
            }
            if (dueOn.isBefore(recordedOn)
                    || charges.existsByLeaseIdAndKindAndPeriodMonth(lease.getId(), Charge.Kind.RENT, period.atDay(1))) {
                continue;
            }
            Charge rent = charges.save(Charge.rent(lease, period, dueOn));
            events.publishEvent(new LedgerChanged(lease.getId(), "charge.added", rent.getDescription(),
                    rent.getAmountPaise(), null));
            created++;
        }
        return created;
    }

    /** The due day of the month, or the move-in date when that comes later in the first month. */
    static LocalDate dueDate(Lease lease, YearMonth period) {
        LocalDate due = period.atDay(lease.getDueDay());
        return due.isBefore(lease.getStartsOn()) ? lease.getStartsOn() : due;
    }

    @Transactional(readOnly = true)
    public Ledger ledger(UUID leaseId, UUID userId, Role role) {
        leaseService.require(leaseId, userId, role);
        LocalDate today = IndiaTime.today(clock);
        List<Charge> found = charges.findByLeaseIdOrderByDueOnAscCreatedAtAsc(leaseId);
        long outstanding = found.stream().filter(Charge::isOpen).mapToLong(Charge::getAmountPaise).sum();
        long overdue = found.stream().filter(charge -> charge.isOverdue(today)).mapToLong(Charge::getAmountPaise).sum();
        return new Ledger(leaseId, outstanding, overdue, found.stream().map(charge -> entry(charge, today)).toList());
    }

    @Transactional
    public Entry addCharge(UUID landlordId, UUID leaseId, Charge.Kind kind, String description, long amountPaise,
                           LocalDate dueOn) {
        Lease lease = leaseService.require(leaseId, landlordId, Role.LANDLORD);
        if (!lease.isLive()) {
            throw ApiException.conflict("lease_ended", "This lease has ended.");
        }
        Charge charge = charges.save(Charge.extra(lease, kind, description, amountPaise, dueOn, landlordId));
        events.publishEvent(new LedgerChanged(leaseId, "charge.added", charge.getDescription(),
                charge.getAmountPaise(), nameOf(landlordId)));
        return entry(charge, IndiaTime.today(clock));
    }

    @Transactional
    public Entry waive(UUID landlordId, UUID chargeId) {
        Charge charge = charges.findById(chargeId).orElseThrow(() -> ApiException.notFound("Charge"));
        leaseService.require(charge.getLeaseId(), landlordId, Role.LANDLORD);
        charge.waive(landlordId, clock.instant());
        events.publishEvent(new LedgerChanged(charge.getLeaseId(), "charge.waived", charge.getDescription(),
                charge.getAmountPaise(), nameOf(landlordId)));
        return entry(charge, IndiaTime.today(clock));
    }

    /** Unpaid charges per lease, for the dashboards. */
    @Transactional(readOnly = true)
    public Map<UUID, List<Charge>> openCharges(Collection<UUID> leaseIds) {
        if (leaseIds.isEmpty()) {
            return Map.of();
        }
        return charges.findByLeaseIdInAndStatus(leaseIds, Charge.Status.DUE).stream()
                .collect(Collectors.groupingBy(Charge::getLeaseId));
    }

    /** Each lease's rent charge for one month, where it exists. */
    @Transactional(readOnly = true)
    public Map<UUID, Charge> rentFor(Collection<UUID> leaseIds, YearMonth period) {
        if (leaseIds.isEmpty()) {
            return Map.of();
        }
        return charges.findByLeaseIdInAndKindAndPeriodMonth(leaseIds, Charge.Kind.RENT, period.atDay(1)).stream()
                .collect(Collectors.toMap(Charge::getLeaseId, Function.identity()));
    }

    /**
     * When the next rent that is not yet on the ledger falls due, so a paid-up tenant still sees what
     * is coming; null once the lease has no further months.
     */
    @Transactional(readOnly = true)
    public LocalDate nextRentDue(Lease lease) {
        LocalDate today = today();
        YearMonth next = charges.findTopByLeaseIdAndKindOrderByPeriodMonthDesc(lease.getId(), Charge.Kind.RENT)
                .map(latest -> YearMonth.from(latest.getPeriodMonth()).plusMonths(1))
                .orElseGet(() -> {
                    YearMonth from = YearMonth.from(lease.getStartsOn().isAfter(today) ? lease.getStartsOn() : today);
                    return dueDate(lease, from).isBefore(today) ? from.plusMonths(1) : from;
                });
        if (lease.getEndsOn() != null && !next.atDay(1).isBefore(lease.getEndsOn())) {
            return null;
        }
        return dueDate(lease, next);
    }

    public static EntryStatus statusOf(Charge charge, LocalDate today) {
        return switch (charge.getStatus()) {
            case PAID -> EntryStatus.PAID;
            case WAIVED -> EntryStatus.WAIVED;
            case DUE -> charge.getDueOn().isAfter(today) ? EntryStatus.UPCOMING
                    : charge.isOverdue(today) ? EntryStatus.OVERDUE : EntryStatus.DUE;
        };
    }

    public LocalDate today() {
        return IndiaTime.today(clock);
    }

    private String nameOf(UUID userId) {
        return users.findById(userId).map(User::getFullName).orElse(null);
    }

    private static Entry entry(Charge charge, LocalDate today) {
        return new Entry(charge.getId(), charge.getKind(), charge.getDescription(), charge.getPeriodMonth(),
                charge.getDueOn(), charge.getAmountPaise(), statusOf(charge, today), charge.getPaidAt(),
                charge.getWaivedAt());
    }
}
