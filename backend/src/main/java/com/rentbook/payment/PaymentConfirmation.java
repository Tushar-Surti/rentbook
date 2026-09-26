package com.rentbook.payment;

import com.rentbook.ledger.Charge;
import com.rentbook.ledger.ChargeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Applies what a verified webhook says to the payment, its charges and the receipt, in one transaction. */
@Service
class PaymentConfirmation {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfirmation.class);

    private final PaymentRepository payments;
    private final ChargeRepository charges;
    private final ReceiptService receipts;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PaymentConfirmation(PaymentRepository payments, ChargeRepository charges, ReceiptService receipts,
                        ApplicationEventPublisher events, Clock clock) {
        this.payments = payments;
        this.charges = charges;
        this.receipts = receipts;
        this.events = events;
        this.clock = clock;
    }

    /** Both parties hear about it after commit; the tenant also gets the receipt by email. */
    record PaymentConfirmed(UUID paymentId, UUID leaseId, UUID tenantId, UUID landlordId, long amountPaise,
                            UUID receiptId, String receiptNumber, Payment.Method method) {
    }

    record PaymentFailed(UUID paymentId, UUID tenantId, String reason) {
    }

    /**
     * order.paid and payment.captured both describe the same success and may arrive in either order;
     * whichever lands first confirms, the other finds the payment already captured.
     */
    @Transactional
    boolean captured(String orderId, String rzpPaymentId, long amountPaise, String currency) {
        Payment payment = orderId == null ? null : payments.lockByOrderId(orderId).orElse(null);
        if (payment == null) {
            log.warn("Razorpay confirmed order {}, which this app did not create", orderId);
            return false;
        }
        if (payment.isCaptured()) {
            return true;
        }
        if (amountPaise != payment.getAmountPaise() || !"INR".equals(currency)) {
            // Money may have moved, so the payment is left as it is for someone to reconcile by hand.
            log.error("Order {} was confirmed for {} {} but was created for {} INR; not marking it paid",
                    orderId, amountPaise, currency, payment.getAmountPaise());
            return false;
        }
        Instant now = clock.instant();
        payment.capture(rzpPaymentId, now);
        for (Charge charge : charges.findAllById(payment.getChargeIds())) {
            if (charge.isOpen()) {
                charge.markPaid(now);
            } else {
                log.warn("Charge {} was already settled when payment {} confirmed it", charge.getId(), payment.getId());
            }
        }
        Receipt receipt = receipts.issue(payment);
        events.publishEvent(new PaymentConfirmed(payment.getId(), payment.getLeaseId(), payment.getTenantId(),
                payment.getLandlordId(), payment.getAmountPaise(), receipt.getId(), receipt.number(),
                Payment.Method.RAZORPAY));
        return true;
    }

    @Transactional
    boolean failed(String orderId, String rzpPaymentId, String reason) {
        Payment payment = orderId == null ? null : payments.lockByOrderId(orderId).orElse(null);
        if (payment == null || payment.isCaptured()) {
            return false;
        }
        payment.fail(rzpPaymentId, reason);
        events.publishEvent(new PaymentFailed(payment.getId(), payment.getTenantId(), reason));
        return true;
    }

    /** The landlord's share moving to their linked account, as Route reports it. */
    @Transactional
    boolean transfer(String rzpPaymentId, String transferId, String status) {
        return payments.findByRzpPaymentId(rzpPaymentId)
                .map(payment -> {
                    payment.transfer(transferId, status);
                    return true;
                })
                .orElse(false);
    }
}
