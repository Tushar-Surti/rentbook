package com.rentbook.caretaker;

import com.rentbook.common.ApiException;
import com.rentbook.dashboard.DashboardService;
import com.rentbook.lease.LeaseService;
import com.rentbook.ledger.LedgerService;
import com.rentbook.maintenance.Ticket;
import com.rentbook.maintenance.TicketService;
import com.rentbook.payment.Payment;
import com.rentbook.payment.RecordedPayments;
import com.rentbook.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * What a caretaker may do, and only on the properties assigned to them: read the register, a lease and its
 * ledger, record rent paid to them directly, and look after repair requests. Every call starts from
 * {@link Caretakers#context}, so access follows the landlord's current assignment and ends at removal.
 * Anything outside their properties answers "not found", exactly as it would for a stranger.
 */
@Service
public class CaretakerWork {

    private final Caretakers caretakers;
    private final DashboardService dashboard;
    private final LeaseService leases;
    private final LedgerService ledger;
    private final RecordedPayments recorded;
    private final TicketService tickets;

    CaretakerWork(Caretakers caretakers, DashboardService dashboard, LeaseService leases, LedgerService ledger,
                  RecordedPayments recorded, TicketService tickets) {
        this.caretakers = caretakers;
        this.dashboard = dashboard;
        this.leases = leases;
        this.ledger = ledger;
        this.recorded = recorded;
        this.tickets = tickets;
    }

    /** The assigned properties' registers, with the totals for those alone. */
    public record Board(String caretakerName, String landlordName, java.time.YearMonth month,
                        DashboardService.Totals totals, List<DashboardService.PropertyBoard> properties) {
    }

    @Transactional(readOnly = true)
    public Board board(UUID userId) {
        Caretakers.Context context = caretakers.context(userId);
        DashboardService.LandlordBoard all = dashboard.landlord(context.landlordId());
        List<DashboardService.PropertyBoard> mine = all.properties().stream()
                .filter(property -> context.propertyIds().contains(property.id())).toList();
        List<DashboardService.Hook> hooks = mine.stream().flatMap(property -> property.hooks().stream()).toList();
        DashboardService.Totals totals = new DashboardService.Totals(hooks.size(),
                (int) hooks.stream().filter(hook -> hook.occupant() != null).count(),
                (int) hooks.stream().filter(hook -> hook.occupant() == null && hook.invite() == null).count(),
                (int) hooks.stream().filter(hook -> hook.invite() != null).count(),
                hooks.stream().filter(hook -> hook.occupant() != null).mapToLong(hook -> hook.occupant().rentPaise()
                        + hook.flatmates().stream().mapToLong(DashboardService.Occupant::rentPaise).sum()).sum());
        return new Board(context.fullName(), context.landlordName(), all.month(), totals, mine);
    }

    @Transactional(readOnly = true)
    public LeaseService.LeaseView lease(UUID userId, UUID leaseId) {
        return lease(caretakers.context(userId), leaseId);
    }

    @Transactional(readOnly = true)
    public LedgerService.Ledger ledger(UUID userId, UUID leaseId) {
        Caretakers.Context context = caretakers.context(userId);
        lease(context, leaseId);
        return ledger.ledger(leaseId, context.landlordId(), Role.LANDLORD);
    }

    @Transactional
    public RecordedPayments.Recorded record(UUID userId, UUID leaseId, Collection<UUID> chargeIds,
                                            Payment.Method method, LocalDate receivedOn, String note) {
        Caretakers.Context context = caretakers.context(userId);
        lease(context, leaseId);
        return recorded.record(context.landlordId(), userId, leaseId, chargeIds, method, receivedOn, note);
    }

    @Transactional(readOnly = true)
    public List<TicketService.TicketView> tickets(UUID userId, UUID propertyId, boolean openOnly) {
        Caretakers.Context context = caretakers.context(userId);
        return tickets.list(context.landlordId(), Role.LANDLORD, propertyId, openOnly).stream()
                .filter(ticket -> context.propertyIds().contains(ticket.property().id())).toList();
    }

    @Transactional(readOnly = true)
    public TicketService.Thread thread(UUID userId, UUID ticketId) {
        Caretakers.Context context = caretakers.context(userId);
        return thread(context, ticketId);
    }

    @Transactional
    public TicketService.EventView post(UUID userId, UUID ticketId, String body) {
        Caretakers.Context context = caretakers.context(userId);
        thread(context, ticketId);
        return tickets.postOnBehalf(ticketId, context.landlordId(), userId, body);
    }

    @Transactional
    public TicketService.Thread move(UUID userId, UUID ticketId, Ticket.Status to) {
        Caretakers.Context context = caretakers.context(userId);
        thread(context, ticketId);
        return tickets.moveOnBehalf(ticketId, context.landlordId(), userId, to);
    }

    /** For live subscriptions: a caretaker may listen to leases and requests on their properties. */
    @Transactional(readOnly = true)
    public boolean canWatchLease(UUID userId, UUID leaseId) {
        try {
            lease(caretakers.context(userId), leaseId);
            return true;
        } catch (ApiException refused) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public boolean canWatchTicket(UUID userId, UUID ticketId) {
        try {
            thread(caretakers.context(userId), ticketId);
            return true;
        } catch (ApiException refused) {
            return false;
        }
    }

    private LeaseService.LeaseView lease(Caretakers.Context context, UUID leaseId) {
        LeaseService.LeaseView lease = leases.view(leaseId, context.landlordId(), Role.LANDLORD);
        if (!context.propertyIds().contains(lease.property().id())) {
            throw ApiException.notFound("Lease");
        }
        return lease;
    }

    private TicketService.Thread thread(Caretakers.Context context, UUID ticketId) {
        TicketService.Thread thread = tickets.threadOnBehalf(ticketId, context.landlordId());
        if (!context.propertyIds().contains(thread.ticket().property().id())) {
            throw ApiException.notFound("Request");
        }
        return thread;
    }
}
