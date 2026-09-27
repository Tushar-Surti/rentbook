package com.rentbook.dashboard;

import com.rentbook.invite.Invite;
import com.rentbook.invite.InviteService;
import com.rentbook.lease.Lease;
import com.rentbook.lease.LeaseRepository;
import com.rentbook.lease.LeaseService;
import com.rentbook.ledger.Charge;
import com.rentbook.ledger.LedgerService;
import com.rentbook.listing.Listings;
import com.rentbook.payment.CheckoutService;
import com.rentbook.payment.DepositSettlements;
import com.rentbook.payment.PayoutService;
import com.rentbook.payment.ReceiptService;
import com.rentbook.property.Property;
import com.rentbook.property.PropertyService;
import com.rentbook.property.Unit;
import com.rentbook.user.Role;
import com.rentbook.user.User;
import com.rentbook.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** What each role needs first: the landlord, who owes what this month; the tenant, what they owe now. */
@Service
public class DashboardService {

    private final PropertyService propertyService;
    private final LeaseRepository leases;
    private final LeaseService leaseService;
    private final InviteService inviteService;
    private final UserRepository users;
    private final LedgerService ledger;
    private final PayoutService payouts;
    private final CheckoutService checkout;
    private final ReceiptService receipts;
    private final DepositSettlements deposits;
    private final Listings listings;
    private final Clock clock;

    DashboardService(PropertyService propertyService, LeaseRepository leases, LeaseService leaseService,
                     InviteService inviteService, UserRepository users, LedgerService ledger, PayoutService payouts,
                     CheckoutService checkout, ReceiptService receipts, DepositSettlements deposits, Listings listings,
                     Clock clock) {
        this.propertyService = propertyService;
        this.leases = leases;
        this.leaseService = leaseService;
        this.inviteService = inviteService;
        this.users = users;
        this.ledger = ledger;
        this.payouts = payouts;
        this.checkout = checkout;
        this.receipts = receipts;
        this.deposits = deposits;
        this.listings = listings;
        this.clock = clock;
    }

    /** This month's rent charge for one tenancy; absent until it has been generated. */
    public record MonthRent(LedgerService.EntryStatus status, long amountPaise, LocalDate dueOn) {
    }

    public record Occupant(UUID leaseId, UUID tenantId, String tenantName, long rentPaise, int dueDay, MonthRent thisMonth,
                    long outstandingPaise) {
    }

    public record OpenInvite(UUID inviteId, String tenantName, Instant expiresAt) {
    }

    /** One leasable unit: a flat, a bed, or a room that is let whole. */
    /** {@code listingId} is set while a vacant unit is listed publicly. */
    public record Hook(UUID unitId, Unit.Kind kind, String label, UUID roomId, String roomLabel, Unit.Status status,
                Occupant occupant, OpenInvite invite, UUID listingId) {
    }

    public record PropertyBoard(UUID id, String name, Property.Kind kind, String city, List<Hook> hooks) {
    }

    public record Totals(int units, int occupied, int vacant, int openInvites, long monthlyRentPaise) {
    }

    /** {@code deposits} are move-outs whose deposit is held and not yet settled. */
    public record LandlordBoard(YearMonth month, Totals totals, List<PropertyBoard> properties,
                         List<DepositSettlements.DepositDue> deposits) {
    }

    public record ChargeLine(UUID id, Charge.Kind kind, String description, LocalDate dueOn, long amountPaise,
                      LedgerService.EntryStatus status) {
    }

    /**
     * {@code payOnline} is false until the landlord's Razorpay payout account is active; {@code pendingPayment}
     * is a payment the browser reported as made that Razorpay has not yet confirmed.
     */
    /**
     * {@code deposit} is the deposit's settlement when one is under way; with no live lease, it belongs to
     * {@code movedOutOf}, the tenant's most recent ended lease.
     */
    public record TenantHome(LeaseService.LeaseView lease, List<ChargeLine> outstanding, long outstandingPaise,
                      boolean overdue, LocalDate nextDueOn, long nextRentPaise, boolean payOnline,
                      CheckoutService.PendingPayment pendingPayment, ReceiptService.ReceiptView lastReceipt,
                      DepositSettlements.DepositView deposit, LeaseService.LeaseView movedOutOf) {
    }

    @Transactional(readOnly = true)
    public LandlordBoard landlord(UUID landlordId) {
        Instant now = clock.instant();
        LocalDate today = ledger.today();
        YearMonth month = YearMonth.from(today);
        Map<UUID, Lease> liveByUnit = leases.findByLandlordIdOrderByStartsOnDesc(landlordId).stream()
                .filter(Lease::isLive)
                .collect(Collectors.toMap(Lease::getUnitId, Function.identity(), (first, second) -> first));
        List<UUID> leaseIds = liveByUnit.values().stream().map(Lease::getId).toList();
        Map<UUID, Charge> rentThisMonth = ledger.rentFor(leaseIds, month);
        Map<UUID, List<Charge>> open = ledger.openCharges(leaseIds);
        Map<UUID, String> tenantNames = users.findAllById(liveByUnit.values().stream().map(Lease::getTenantId).toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        Map<UUID, UUID> listed = listings.openByUnit(landlordId);
        Map<UUID, Invite> openByUnit = inviteService.list(landlordId).stream()
                .filter(invite -> invite.isOpen(now))
                .collect(Collectors.toMap(Invite::getUnitId, Function.identity(), (first, second) -> first));

        List<PropertyBoard> boards = propertyService.portfolio(landlordId).stream().map(entry -> {
            Map<UUID, Unit> byId = entry.units().stream().collect(Collectors.toMap(Unit::getId, Function.identity()));
            Set<UUID> roomsWithBeds = entry.units().stream().map(Unit::getParentUnitId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            List<Hook> hooks = entry.units().stream()
                    .filter(unit -> !roomsWithBeds.contains(unit.getId()))
                    .map(unit -> {
                        Unit room = unit.getParentUnitId() == null ? null : byId.get(unit.getParentUnitId());
                        Lease lease = liveByUnit.get(unit.getId());
                        Invite invite = openByUnit.get(unit.getId());
                        Occupant occupant = lease == null ? null : new Occupant(lease.getId(), lease.getTenantId(),
                                tenantNames.get(lease.getTenantId()), lease.getRentPaise(), lease.getDueDay(),
                                monthRent(rentThisMonth.get(lease.getId()), today),
                                sum(open.getOrDefault(lease.getId(), List.of())));
                        return new Hook(unit.getId(), unit.getKind(), unit.getLabel(),
                                room == null ? null : room.getId(), room == null ? null : room.getLabel(),
                                unit.getStatus(), occupant,
                                invite == null ? null : new OpenInvite(invite.getId(), invite.getTenantName(),
                                        invite.getExpiresAt()),
                                lease == null ? listed.get(unit.getId()) : null);
                    })
                    .toList();
            Property property = entry.property();
            return new PropertyBoard(property.getId(), property.getName(), property.getKind(), property.getCity(), hooks);
        }).toList();

        List<Hook> all = boards.stream().flatMap(board -> board.hooks().stream()).toList();
        int occupied = (int) all.stream().filter(hook -> hook.occupant() != null).count();
        int vacant = (int) all.stream().filter(hook -> hook.status() == Unit.Status.VACANT).count();
        long rent = liveByUnit.values().stream().mapToLong(Lease::getRentPaise).sum();
        return new LandlordBoard(month, new Totals(all.size(), occupied, vacant, openByUnit.size(), rent), boards,
                deposits.due(landlordId));
    }

    @Transactional(readOnly = true)
    TenantHome tenant(UUID tenantId) {
        List<LeaseService.LeaseView> own = leaseService.visibleTo(tenantId, Role.TENANT);
        LeaseService.LeaseView current = own.stream()
                .filter(view -> view.status() != Lease.Status.ENDED)
                .findFirst().orElse(null);
        if (current == null) {
            LeaseService.LeaseView ended = own.stream().findFirst().orElse(null);
            DepositSettlements.DepositView deposit = ended == null ? null : deposits.forTenant(ended.id(), tenantId);
            return new TenantHome(null, List.of(), 0, false, null, 0, false, null, null, deposit,
                    deposit == null ? null : ended);
        }
        LocalDate today = ledger.today();
        List<Charge> open = ledger.openCharges(List.of(current.id())).getOrDefault(current.id(), List.of()).stream()
                .sorted(Comparator.comparing(Charge::getDueOn))
                .toList();
        List<ChargeLine> lines = open.stream()
                .map(charge -> new ChargeLine(charge.getId(), charge.getKind(), charge.getDescription(),
                        charge.getDueOn(), charge.getAmountPaise(), LedgerService.statusOf(charge, today)))
                .toList();
        Lease lease = leaseService.require(current.id(), tenantId, Role.TENANT);
        return new TenantHome(current, lines, sum(open), open.stream().anyMatch(charge -> charge.isOverdue(today)),
                ledger.nextRentDue(lease), current.rentPaise(), payouts.acceptsOnlinePayments(lease.getLandlordId()),
                checkout.pending(current.id()).orElse(null), receipts.latestForLease(current.id()).orElse(null),
                deposits.forTenant(current.id(), tenantId), null);
    }

    private static MonthRent monthRent(Charge charge, LocalDate today) {
        return charge == null ? null
                : new MonthRent(LedgerService.statusOf(charge, today), charge.getAmountPaise(), charge.getDueOn());
    }

    private static long sum(List<Charge> charges) {
        return charges.stream().mapToLong(Charge::getAmountPaise).sum();
    }
}
