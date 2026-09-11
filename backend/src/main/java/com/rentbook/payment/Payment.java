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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One checkout of one or more charges. Nothing counts as paid until Razorpay's signed webhook says the
 * order was paid: the browser's success callback only moves a payment to AWAITING_WEBHOOK.
 */
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    public enum Status { CREATED, AWAITING_WEBHOOK, CAPTURED, FAILED }

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

    public Set<UUID> getChargeIds() {
        return Set.copyOf(chargeIds);
    }
}
