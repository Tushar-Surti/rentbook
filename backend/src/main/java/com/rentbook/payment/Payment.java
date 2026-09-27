package com.rentbook.payment;

import com.rentbook.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One payment of one or more charges. Online, nothing counts as paid until Razorpay's signed webhook
 * says the order was paid: the browser's success callback only moves a payment to AWAITING_WEBHOOK.
 * A landlord can also record money they received themselves (cash, UPI, a transfer, a cheque); that
 * payment is captured on the landlord's word and carries no platform fee.
 */
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    public enum Status { CREATED, AWAITING_WEBHOOK, CAPTURED, FAILED }

    /** How the money arrived. Every method but RAZORPAY is recorded by the landlord. */
    public enum Method {
        RAZORPAY("online through Razorpay"),
        CASH("in cash"),
        UPI("by UPI"),
        BANK_TRANSFER("by bank transfer"),
        CHEQUE("by cheque"),
        /** Settled from the tenant's security deposit, as both agreed at move-out. */
        DEPOSIT("from the security deposit");

        private final String phrase;

        Method(String phrase) {
            this.phrase = phrase;
        }

        /** As it reads in a sentence: "Received in cash". */
        public String phrase() {
            return phrase;
        }
    }

    @Column(name = "lease_id", nullable = false, updatable = false)
    private UUID leaseId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "landlord_id", nullable = false, updatable = false)
    private UUID landlordId;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Column(name = "platform_fee_paise", nullable = false, updatable = false)
    private long platformFeePaise;

    @Column(name = "landlord_share_paise", nullable = false, updatable = false)
    private long landlordSharePaise;

    @Column(name = "rzp_order_id", length = 40)
    private String rzpOrderId;

    @Column(name = "rzp_payment_id", length = 40)
    private String rzpPaymentId;

    @Column(name = "rzp_transfer_id", length = 40)
    private String rzpTransferId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status = Status.CREATED;

    @Column(name = "transfer_status", length = 24)
    private String transferStatus;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Method method = Method.RAZORPAY;

    @Column(name = "received_on", updatable = false)
    private LocalDate receivedOn;

    @Column(length = 200, updatable = false)
    private String note;

    @Column(name = "recorded_by", updatable = false)
    private UUID recordedBy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "payment_charges", joinColumns = @JoinColumn(name = "payment_id"))
    @Column(name = "charge_id", nullable = false)
    private Set<UUID> chargeIds = new HashSet<>();

    protected Payment() {
    }

    Payment(UUID leaseId, UUID tenantId, UUID landlordId, long amountPaise, long platformFeePaise,
            Collection<UUID> chargeIds) {
        this.leaseId = leaseId;
        this.tenantId = tenantId;
        this.landlordId = landlordId;
        this.amountPaise = amountPaise;
        this.platformFeePaise = platformFeePaise;
        this.landlordSharePaise = amountPaise - platformFeePaise;
        this.chargeIds.addAll(chargeIds);
    }

    /**
     * Money the landlord's side says was received (by the landlord, or by their caretaker, {@code recordedBy}):
     * paid in full to the landlord, and captured at once.
     */
    static Payment recordedByLandlord(UUID leaseId, UUID tenantId, UUID landlordId, UUID recordedBy, long amountPaise,
                                      Collection<UUID> chargeIds, Method method, LocalDate receivedOn, String note,
                                      Instant now) {
        Payment payment = new Payment(leaseId, tenantId, landlordId, amountPaise, 0, chargeIds);
        payment.method = method;
        payment.receivedOn = receivedOn;
        payment.note = note;
        payment.recordedBy = recordedBy;
        payment.status = Status.CAPTURED;
        payment.capturedAt = now;
        return payment;
    }

    void attachOrder(String orderId) {
        this.rzpOrderId = orderId;
    }

    /** The browser says the checkout succeeded; the webhook still has to confirm it. */
    void awaitWebhook(String paymentId) {
        if (status == Status.CREATED) {
            status = Status.AWAITING_WEBHOOK;
            rzpPaymentId = paymentId;
        }
    }

    void capture(String paymentId, Instant now) {
        status = Status.CAPTURED;
        rzpPaymentId = paymentId;
        capturedAt = now;
        failureReason = null;
    }

    void fail(String paymentId, String reason) {
        if (status != Status.CAPTURED) {
            status = Status.FAILED;
            rzpPaymentId = paymentId;
            failureReason = reason == null ? null : reason.substring(0, Math.min(300, reason.length()));
        }
    }

    void transfer(String transferId, String transferStatus) {
        this.rzpTransferId = transferId;
        this.transferStatus = transferStatus;
    }

    public boolean isCaptured() {
        return status == Status.CAPTURED;
    }

    public UUID getLeaseId() {
        return leaseId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getLandlordId() {
        return landlordId;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public long getPlatformFeePaise() {
        return platformFeePaise;
    }

    public long getLandlordSharePaise() {
        return landlordSharePaise;
    }

    public String getRzpOrderId() {
        return rzpOrderId;
    }

    public String getRzpPaymentId() {
        return rzpPaymentId;
    }

    public Status getStatus() {
        return status;
    }

    public String getTransferStatus() {
        return transferStatus;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public Method getMethod() {
        return method;
    }

    public boolean isRecordedByLandlord() {
        return method != Method.RAZORPAY;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public LocalDate getReceivedOn() {
        return receivedOn;
    }

    public String getNote() {
        return note;
    }

    public Set<UUID> getChargeIds() {
        return Set.copyOf(chargeIds);
    }
}
