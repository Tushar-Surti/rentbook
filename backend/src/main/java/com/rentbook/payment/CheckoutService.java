package com.rentbook.payment;

import com.rentbook.common.ApiException;
import com.rentbook.lease.Lease;
import com.rentbook.lease.LeaseService;
import com.rentbook.ledger.Charge;
import com.rentbook.ledger.ChargeRepository;
import com.rentbook.user.Role;
import com.rentbook.user.User;
import com.rentbook.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Starts a Razorpay Route checkout for a tenant's unpaid charges. The order carries a transfer of the
 * landlord's share to their linked account; the platform fee stays with the platform account.
 * Confirmation comes only from the signed webhook, never from the browser.
 */
@Service
public class CheckoutService {

    /**
     * Razorpay retries a webhook for about a day. Until the confirmation lands or that day passes, a
     * payment the browser reported as made blocks a second payment for the same charges.
     */
    static final Duration CONFIRMATION_WINDOW = Duration.ofHours(24);

    /** Razorpay won't create an order for less than ₹1. */
    static final long MINIMUM_ORDER_PAISE = 100;

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final PaymentRepository payments;
    private final ChargeRepository charges;
    private final LeaseService leases;
    private final PayoutAccountRepository payoutAccounts;
    private final UserRepository users;
    private final RazorpayGateway gateway;
    private final RazorpayProperties razorpay;
    private final PayoutService payouts;
    private final Clock clock;

    CheckoutService(PaymentRepository payments, ChargeRepository charges, LeaseService leases,
                    PayoutAccountRepository payoutAccounts, UserRepository users, RazorpayGateway gateway,
                    RazorpayProperties razorpay, PayoutService payouts, Clock clock) {
        this.payments = payments;
        this.charges = charges;
        this.leases = leases;
        this.payoutAccounts = payoutAccounts;
        this.users = users;
        this.gateway = gateway;
        this.razorpay = razorpay;
        this.payouts = payouts;
        this.clock = clock;
    }

    public record Prefill(String name, String email, String contact) {
    }

    /** What the browser needs to open Razorpay Checkout. The key id is public by design. */
    public record Checkout(UUID paymentId, String orderId, String keyId, long amountPaise, String currency,
                           String description, Prefill prefill) {
    }

    /** A payment the browser reported as made, still waiting for Razorpay's webhook. */
    public record PendingPayment(UUID id, long amountPaise) {
    }

    @Transactional
    public Checkout start(UUID tenantId, UUID leaseId, List<UUID> chargeIds) {
        payouts.requireConfigured();
        Lease lease = leases.require(leaseId, tenantId, Role.TENANT);
        List<Charge> selected = chargeIds == null || chargeIds.isEmpty()
                ? charges.findByLeaseIdInAndStatus(List.of(leaseId), Charge.Status.DUE)
                : charges.findAllById(chargeIds);
        if (selected.isEmpty()) {
            throw ApiException.badRequest("nothing_to_pay", "There's nothing to pay right now.");
        }
        if (chargeIds != null && !chargeIds.isEmpty() && selected.size() != chargeIds.size()) {
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
        List<UUID> ids = selected.stream().map(Charge::getId).toList();
        if (payments.anyCoverWithStatus(ids, Payment.Status.CAPTURED)) {
            throw ApiException.conflict("already_paid", "Some of these charges have already been paid.");
        }
        if (payments.anyCoverWithStatusSince(ids, Payment.Status.AWAITING_WEBHOOK, confirmationCutoff())) {
            throw ApiException.conflict("payment_pending",
                    "Your last payment is still waiting for the bank to confirm it. Your rent book updates when it does.");
        }
        PayoutAccount payout = payoutAccounts.findById(lease.getLandlordId()).filter(PayoutAccount::isActive)
                .orElseThrow(() -> ApiException.conflict("landlord_not_ready",
                        "Your landlord hasn't set up online payments yet. Pay them the way you do now."));

        long amount = selected.stream().mapToLong(Charge::getAmountPaise).sum();
        if (amount < MINIMUM_ORDER_PAISE) {
            throw ApiException.badRequest("amount_too_small",
                    "Online payments start at ₹1. Pay this one to your landlord the way you do now.");
        }
        long fee = platformFee(amount, payouts.feeBps(lease.getLandlordId()));
        Payment payment = payments.save(new Payment(leaseId, tenantId, lease.getLandlordId(), amount, fee, ids));

        Map<String, String> notes = Map.of("payment", payment.getId().toString(), "lease", leaseId.toString());
        RazorpayGateway.Order order;
        try {
            order = gateway.createOrder(amount, payment.getId().toString(), notes,
                    new RazorpayGateway.Transfer(payout.getRzpAccountId(), amount - fee, notes));
        } catch (RazorpayException e) {
            if (e.isAuthFailure()) {
                // The server's keys, not the tenant's session: a 401 here would send the browser off to refresh its login.
                log.error("Razorpay rejected the API keys while creating an order; check RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET");
                throw ApiException.serviceUnavailable("payments_misconfigured",
                        "Online payments aren't working on this server right now. Pay your landlord the way you do now.");
            }
            log.warn("Razorpay refused an order for payment {}", payment.getId(), e);
            throw ApiException.badGateway("razorpay_order_failed", "Couldn't start the payment. Try again in a moment.");
        }
        payment.attachOrder(order.id());

        User tenant = users.findById(tenantId).orElseThrow(() -> ApiException.notFound("Tenant"));
        String description = selected.size() == 1 ? selected.getFirst().getDescription() : selected.size() + " charges";
        return new Checkout(payment.getId(), order.id(), razorpay.keyId(), amount, "INR", description,
                new Prefill(tenant.getFullName(), tenant.getEmail(), tenant.getPhone()));
    }

    /**
     * The browser's success callback. Its signature is checked, but it only moves the payment to
     * AWAITING_WEBHOOK: charges stay unpaid until Razorpay's signed webhook confirms the order.
     */
    @Transactional
    public Payment browserSaysPaid(UUID tenantId, UUID paymentId, String orderId, String rzpPaymentId, String signature) {
        Payment payment = payments.findByIdAndTenantId(paymentId, tenantId).orElseThrow(() -> ApiException.notFound("Payment"));
        if (!orderId.equals(payment.getRzpOrderId())
                || !Signatures.checkoutValid(orderId, rzpPaymentId, signature, razorpay.keySecret())) {
            throw ApiException.badRequest("bad_signature", "That payment confirmation didn't check out.");
        }
        payment.awaitWebhook(rzpPaymentId);
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment status(UUID tenantId, UUID paymentId) {
        return payments.findByIdAndTenantId(paymentId, tenantId).orElseThrow(() -> ApiException.notFound("Payment"));
    }

    /** The lease's latest payment still waiting for its webhook, so a reloaded slip keeps waiting too. */
    @Transactional(readOnly = true)
    public Optional<PendingPayment> pending(UUID leaseId) {
        return payments.findFirstByLeaseIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(leaseId,
                        Payment.Status.AWAITING_WEBHOOK, confirmationCutoff())
                .map(payment -> new PendingPayment(payment.getId(), payment.getAmountPaise()));
    }

    /** Basis points of the amount, rounded half up to the paisa. */
    static long platformFee(long amountPaise, int feeBps) {
        return (amountPaise * feeBps + 5_000) / 10_000;
    }

    private Instant confirmationCutoff() {
        return clock.instant().minus(CONFIRMATION_WINDOW);
    }
}
