package com.rentbook.payment;

import com.rentbook.common.ApiException;
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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Money a landlord received outside Rentbook: cash in hand, UPI to their own account, a bank transfer or a
 * cheque. Recording it settles the charges, issues the next receipt in their book, and tells both parties,
 * exactly as a confirmed online payment does. The receipt says it was recorded by the landlord.
 */
@Service
public class RecordedPayments {

    private final LeaseService leases;
    private final ChargeRepository charges;
    private final PaymentRepository payments;
    private final ReceiptService receipts;
    private final LedgerService ledger;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    RecordedPayments(LeaseService leases, ChargeRepository charges, PaymentRepository payments, ReceiptService receipts,
                     LedgerService ledger, ApplicationEventPublisher events, Clock clock) {
        this.leases = leases;
        this.charges = charges;
        this.payments = payments;
        this.receipts = receipts;
        this.ledger = ledger;
        this.events = events;
        this.clock = clock;
    }

    public record Recorded(UUID paymentId, UUID receiptId, String receiptNumber, long amountPaise) {
    }

    @Transactional
    public Recorded record(UUID landlordId, UUID leaseId, Collection<UUID> chargeIds, Payment.Method method,
                    LocalDate receivedOn, String note) {
        if (method == Payment.Method.RAZORPAY) {
            throw ApiException.badRequest("wrong_method", "Online payments are recorded when Razorpay confirms them.");
        }
        Lease lease = leases.require(leaseId, landlordId, Role.LANDLORD);
        if (receivedOn.isAfter(ledger.today())) {
            throw ApiException.badRequest("received_in_future", "The day you received it can't be in the future.");
        }
        Set<UUID> ids = new LinkedHashSet<>(chargeIds);
        List<Charge> selected = charges.findAllById(ids);
        if (ids.isEmpty()) {
            throw ApiException.badRequest("nothing_selected", "Pick at least one charge that was paid.");
        }
        if (selected.size() != ids.size()) {
            throw ApiException.notFound("Charge");
        }
        for (Charge charge : selected) {
            if (!charge.getLeaseId().equals(leaseId)) {
                throw ApiException.notFound("Charge");
            }
            if (!charge.isOpen()) {
                throw ApiException.conflict("already_settled", charge.getDescription() + " is already settled.");
            }
        }
        Instant now = clock.instant();
        // An online payment for these charges may still be on its way; recording them too would take the rent twice.
        if (payments.anyCoverWithStatusSince(ids, Payment.Status.AWAITING_WEBHOOK, now.minus(CheckoutService.CONFIRMATION_WINDOW))) {
            throw ApiException.conflict("payment_pending",
                    "The tenant paid some of these online and the bank hasn't confirmed it yet. Wait for that first.");
        }

        long amount = selected.stream().mapToLong(Charge::getAmountPaise).sum();
        String cleanNote = note == null || note.isBlank() ? null : note.strip();
        Payment payment = payments.save(Payment.recordedByLandlord(leaseId, lease.getTenantId(), landlordId, amount,
                ids, method, receivedOn, cleanNote, now));
        selected.forEach(charge -> charge.markPaid(now));
        Receipt receipt = receipts.issue(payment);
        events.publishEvent(new PaymentConfirmation.PaymentConfirmed(payment.getId(), leaseId, lease.getTenantId(),
                landlordId, amount, receipt.getId(), receipt.number(), method));
        return new Recorded(payment.getId(), receipt.getId(), receipt.number(), amount);
    }
}
