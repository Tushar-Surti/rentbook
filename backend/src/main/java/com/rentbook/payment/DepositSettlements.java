package com.rentbook.payment;

import com.rentbook.common.ApiException;
import com.rentbook.common.Rupees;
import com.rentbook.lease.Lease;
import com.rentbook.lease.LeaseService;
import com.rentbook.ledger.Charge;
import com.rentbook.ledger.ChargeRepository;
import com.rentbook.ledger.LedgerService;
import com.rentbook.user.Role;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Settling the security deposit when a tenant moves out. Both parties read the same statement: what was
 * held, each deduction, and what comes back. Deductions that clear an unpaid charge are paid from the
 * deposit only once the tenant agrees, and get a receipt like any other payment.
 */
@Service
public class DepositSettlements {

    private final LeaseService leases;
    private final ChargeRepository charges;
    private final PaymentRepository payments;
    private final ReceiptService receipts;
    private final LedgerService ledger;
    private final DepositSettlementRepository settlements;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    DepositSettlements(LeaseService leases, ChargeRepository charges, PaymentRepository payments,
                       ReceiptService receipts, LedgerService ledger, DepositSettlementRepository settlements,
                       ApplicationEventPublisher events, Clock clock) {
        this.leases = leases;
        this.charges = charges;
        this.payments = payments;
        this.receipts = receipts;
        this.ledger = ledger;
        this.settlements = settlements;
        this.events = events;
        this.clock = clock;
    }

    /** A deduction as sent: what it's for and how much, or just {@code chargeId} to clear an unpaid charge. */
    public record DeductionLine(String description, Long amountPaise, UUID chargeId) {
    }

    public record Refund(Payment.Method method, LocalDate refundedOn, String reference) {
    }

    public record SettlementView(DepositSettlement.Status status, long heldPaise, List<DeductionLine> deductions,
                                 long deductedPaise, long refundPaise, String landlordNote, String tenantNote,
                                 Instant proposedAt, Instant respondedAt, Refund refund, Instant settledAt) {
    }

    public record OpenCharge(UUID id, String description, long amountPaise, LocalDate dueOn) {
    }

    /**
     * {@code heldPaise} is the deposit the tenant has paid. {@code canPropose} is true once the last day is set,
     * something is held, and nothing has been agreed yet. {@code openCharges} are the landlord's to deduct.
     */
    public record DepositView(UUID leaseId, Lease.Status leaseStatus, long heldPaise, boolean canPropose,
                              SettlementView settlement, List<OpenCharge> openCharges) {
    }

    /** A move-out whose deposit isn't settled yet, for the landlord's book. */
    public record DepositDue(UUID leaseId, UUID propertyId, String tenantName, String unitLabel, String roomLabel,
                             LocalDate lastDay, Lease.Status leaseStatus, long heldPaise,
                             DepositSettlement.Status status) {
    }

    /** Published inside the transaction; {@link DepositNotifications} tells the other side after commit. */
    public record DepositChanged(LeaseService.LeaseView lease, SettlementView settlement) {
    }

    @Transactional(readOnly = true)
    public DepositView view(UUID leaseId, UUID userId, Role role) {
        Lease lease = leases.require(leaseId, userId, role);
        return view(lease, settlements.findByLeaseId(leaseId).orElse(null), role == Role.LANDLORD);
    }

    @Transactional
    public DepositView propose(UUID landlordId, UUID leaseId, List<DeductionLine> lines, String note) {
        Lease lease = leases.require(leaseId, landlordId, Role.LANDLORD);
        if (lease.getStatus() == Lease.Status.ACTIVE) {
            throw ApiException.conflict("lease_active", "Set the tenant's last day first; then settle the deposit.");
        }
        DepositSettlement settlement = settlements.findByLeaseId(leaseId).orElse(null);
        if (settlement != null && !settlement.isOpenToChange()) {
            throw ApiException.conflict("settlement_agreed", "This deposit settlement is already agreed.");
        }
        long held = held(leaseId);
        if (held == 0) {
            throw ApiException.conflict("no_deposit",
                    "No deposit has been paid on this lease. If the tenant paid you one, record it on the rent book first.");
        }
        List<DepositSettlement.Deduction> deductions = checked(lease, lines);
        long deducted = deductions.stream().mapToLong(DepositSettlement.Deduction::amountPaise).sum();
        if (deducted > held) {
            throw ApiException.badRequest("deductions_exceed_deposit",
                    "The deductions come to " + Rupees.format(deducted) + ", more than the " + Rupees.format(held)
                            + " deposit held. Anything beyond the deposit stays on the rent book.");
        }
        if (settlement == null) {
            settlement = new DepositSettlement(leaseId, landlordId, lease.getTenantId());
        }
        settlement.propose(held, deductions, clean(note), clock.instant());
        settlements.save(settlement);
        return changed(lease, settlement, true);
    }

    @Transactional
    public DepositView query(UUID tenantId, UUID leaseId, String note) {
        Lease lease = leases.require(leaseId, tenantId, Role.TENANT);
        DepositSettlement settlement = awaitingTenant(leaseId);
        String question = clean(note);
        if (question == null) {
            throw ApiException.badRequest("empty_question", "Say what you'd like your landlord to look at again.");
        }
        settlement.query(question, clock.instant());
        return changed(lease, settlement, false);
    }

    /** The tenant agrees. Unpaid charges on the statement are paid from the deposit, with a receipt. */
    @Transactional
    public DepositView accept(UUID tenantId, UUID leaseId) {
        Lease lease = leases.require(leaseId, tenantId, Role.TENANT);
        DepositSettlement settlement = awaitingTenant(leaseId);
        Instant now = clock.instant();
        List<UUID> chargeIds = settlement.getDeductions().stream().map(DepositSettlement.Deduction::chargeId)
                .filter(Objects::nonNull).toList();
        if (!chargeIds.isEmpty()) {
            List<Charge> cleared = charges.findAllById(chargeIds);
            boolean stale = cleared.size() != chargeIds.size() || cleared.stream().anyMatch(charge -> !charge.isOpen())
                    || payments.anyCoverWithStatusSince(chargeIds, Payment.Status.AWAITING_WEBHOOK,
                    now.minus(CheckoutService.CONFIRMATION_WINDOW));
            if (stale) {
                throw ApiException.conflict("settlement_changed",
                        "Something on the rent book has changed since this was sent. Ask your landlord to send it again.");
            }
            long amount = cleared.stream().mapToLong(Charge::getAmountPaise).sum();
            Payment payment = payments.save(Payment.recordedByLandlord(leaseId, tenantId, lease.getLandlordId(),
                    lease.getLandlordId(), amount, chargeIds, Payment.Method.DEPOSIT, ledger.today(),
                    "Agreed in the deposit settlement", now));
            cleared.forEach(charge -> charge.markPaid(now));
            Receipt receipt = receipts.issue(payment);
            events.publishEvent(new PaymentConfirmation.PaymentConfirmed(payment.getId(), leaseId, tenantId,
                    lease.getLandlordId(), amount, receipt.getId(), receipt.number(), Payment.Method.DEPOSIT,
                    lease.getLandlordId()));
        }
        settlement.accept(now);
        return changed(lease, settlement, false);
    }

    @Transactional
    public DepositView refund(UUID landlordId, UUID leaseId, Payment.Method method, LocalDate refundedOn,
                              String reference) {
        Lease lease = leases.require(leaseId, landlordId, Role.LANDLORD);
        DepositSettlement settlement = settlements.findByLeaseId(leaseId)
                .orElseThrow(() -> ApiException.notFound("Deposit settlement"));
        if (settlement.getStatus() != DepositSettlement.Status.ACCEPTED) {
            throw ApiException.conflict("not_accepted", settlement.getStatus() == DepositSettlement.Status.SETTLED
                    ? "This deposit is already settled." : "Record the refund once the tenant has accepted the settlement.");
        }
        if (method == Payment.Method.RAZORPAY || method == Payment.Method.DEPOSIT) {
            throw ApiException.badRequest("wrong_method", "Say how you paid the refund: cash, UPI, bank transfer or cheque.");
        }
        if (refundedOn.isAfter(ledger.today())) {
            throw ApiException.badRequest("refund_in_future", "The day you paid the refund can't be in the future.");
        }
        settlement.refund(method, refundedOn, clean(reference), clock.instant());
        return changed(lease, settlement, true);
    }

    /** Move-outs across the landlord's leases whose deposit is held and not yet settled. */
    @Transactional(readOnly = true)
    public List<DepositDue> due(UUID landlordId) {
        List<LeaseService.LeaseView> leaving = leases.visibleTo(landlordId, Role.LANDLORD).stream()
                .filter(lease -> lease.status() != Lease.Status.ACTIVE).toList();
        if (leaving.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = leaving.stream().map(LeaseService.LeaseView::id).toList();
        Map<UUID, Long> held = heldByLease(ids);
        Map<UUID, DepositSettlement> byLease = settlements.findByLeaseIdIn(ids).stream()
                .collect(Collectors.toMap(DepositSettlement::getLeaseId, Function.identity()));
        List<DepositDue> due = new ArrayList<>();
        for (LeaseService.LeaseView lease : leaving) {
            DepositSettlement settlement = byLease.get(lease.id());
            long amount = settlement != null ? settlement.getHeldPaise() : held.getOrDefault(lease.id(), 0L);
            if (amount > 0 && (settlement == null || settlement.getStatus() != DepositSettlement.Status.SETTLED)) {
                due.add(new DepositDue(lease.id(), lease.property().id(), lease.tenant().fullName(), lease.unit().label(),
                        lease.unit().roomLabel(), lease.endsOn(), lease.status(), amount,
                        settlement == null ? null : settlement.getStatus()));
            }
        }
        return due;
    }

    /** The tenant's side of a lease's deposit, for their home page; null when there is nothing to show. */
    @Transactional(readOnly = true)
    public DepositView forTenant(UUID leaseId, UUID tenantId) {
        Lease lease = leases.require(leaseId, tenantId, Role.TENANT);
        DepositSettlement settlement = settlements.findByLeaseId(leaseId).orElse(null);
        if (settlement == null && (lease.getStatus() == Lease.Status.ACTIVE || held(leaseId) == 0)) {
            return null;
        }
        return view(lease, settlement, false);
    }

    private DepositSettlement awaitingTenant(UUID leaseId) {
        DepositSettlement settlement = settlements.findByLeaseId(leaseId)
                .orElseThrow(() -> ApiException.notFound("Deposit settlement"));
        if (settlement.getStatus() != DepositSettlement.Status.PROPOSED) {
            throw ApiException.conflict("not_awaiting_you", settlement.getStatus() == DepositSettlement.Status.QUERIED
                    ? "You've asked about this. Your landlord will send an updated settlement."
                    : "This settlement is already agreed.");
        }
        return settlement;
    }

    private List<DepositSettlement.Deduction> checked(Lease lease, List<DeductionLine> lines) {
        List<DepositSettlement.Deduction> checked = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        Map<UUID, Charge> open = charges.findByLeaseIdInAndStatus(List.of(lease.getId()), Charge.Status.DUE).stream()
                .filter(charge -> charge.getKind() != Charge.Kind.DEPOSIT)
                .collect(Collectors.toMap(Charge::getId, Function.identity()));
        for (DeductionLine line : lines == null ? List.<DeductionLine>of() : lines) {
            if (line.chargeId() != null) {
                Charge charge = open.get(line.chargeId());
                if (charge == null || !seen.add(charge.getId())) {
                    throw ApiException.badRequest("charge_not_open",
                            "Only unpaid charges on this rent book can be taken from the deposit, each once.");
                }
                checked.add(new DepositSettlement.Deduction(charge.getDescription(), charge.getAmountPaise(),
                        charge.getId()));
                continue;
            }
            String description = clean(line.description());
            if (description == null || line.amountPaise() == null || line.amountPaise() <= 0) {
                throw ApiException.badRequest("deduction_incomplete", "Give each deduction what it's for and an amount.");
            }
            checked.add(new DepositSettlement.Deduction(description.length() > 160 ? description.substring(0, 160)
                    : description, line.amountPaise(), null));
        }
        return checked;
    }

    private DepositView changed(Lease lease, DepositSettlement settlement, boolean landlord) {
        DepositView view = view(lease, settlement, landlord);
        events.publishEvent(new DepositChanged(leases.view(lease.getId(), lease.getLandlordId(), Role.LANDLORD),
                view.settlement()));
        return view;
    }

    private DepositView view(Lease lease, DepositSettlement settlement, boolean landlord) {
        long held = settlement != null && settlement.getStatus() != DepositSettlement.Status.PROPOSED
                && settlement.getStatus() != DepositSettlement.Status.QUERIED
                ? settlement.getHeldPaise() : held(lease.getId());
        boolean canPropose = lease.getStatus() != Lease.Status.ACTIVE && held > 0
                && (settlement == null || settlement.isOpenToChange());
        List<OpenCharge> open = !landlord ? List.of()
                : charges.findByLeaseIdInAndStatus(List.of(lease.getId()), Charge.Status.DUE).stream()
                .filter(charge -> charge.getKind() != Charge.Kind.DEPOSIT)
                .map(charge -> new OpenCharge(charge.getId(), charge.getDescription(), charge.getAmountPaise(),
                        charge.getDueOn()))
                .toList();
        return new DepositView(lease.getId(), lease.getStatus(), held, canPropose,
                settlement == null ? null : settlementView(settlement), open);
    }

    private static SettlementView settlementView(DepositSettlement settlement) {
        return new SettlementView(settlement.getStatus(), settlement.getHeldPaise(),
                settlement.getDeductions().stream()
                        .map(line -> new DeductionLine(line.description(), line.amountPaise(), line.chargeId()))
                        .toList(),
                settlement.deductedPaise(), settlement.refundPaise(), settlement.getLandlordNote(),
                settlement.getTenantNote(), settlement.getProposedAt(), settlement.getRespondedAt(),
                settlement.getRefundMethod() == null ? null : new Refund(settlement.getRefundMethod(),
                        settlement.getRefundedOn(), settlement.getRefundReference()),
                settlement.getSettledAt());
    }

    /** The deposit the tenant has actually paid: paid deposit charges on the lease. */
    private long held(UUID leaseId) {
        return heldByLease(List.of(leaseId)).getOrDefault(leaseId, 0L);
    }

    private Map<UUID, Long> heldByLease(Collection<UUID> leaseIds) {
        return charges.findByLeaseIdInAndStatus(leaseIds, Charge.Status.PAID).stream()
                .filter(charge -> charge.getKind() == Charge.Kind.DEPOSIT)
                .collect(Collectors.groupingBy(Charge::getLeaseId, Collectors.summingLong(Charge::getAmountPaise)));
    }

    private static String clean(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String stripped = text.strip();
        return stripped.length() > 500 ? stripped.substring(0, 500) : stripped;
    }
}
