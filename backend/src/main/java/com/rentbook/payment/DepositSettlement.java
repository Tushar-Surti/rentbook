package com.rentbook.payment;

import com.rentbook.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The security deposit's settlement at move-out, one per lease. The landlord proposes deductions; the
 * tenant accepts them or asks about them, and the landlord may revise and send them again; once agreed,
 * the landlord records the refund. The deposit held is fixed when the settlement is proposed.
 */
@Entity
@Table(name = "deposit_settlements")
public class DepositSettlement extends BaseEntity {

    public enum Status { PROPOSED, QUERIED, ACCEPTED, SETTLED }

    /** One line taken off the deposit. {@code chargeId} is set when it clears an unpaid charge on the ledger. */
    @Embeddable
    public record Deduction(
            @Column(nullable = false, length = 160) String description,
            @Column(name = "amount_paise", nullable = false) long amountPaise,
            @Column(name = "charge_id") UUID chargeId) {
    }

    @Column(name = "lease_id", nullable = false, updatable = false)
    private UUID leaseId;

    @Column(name = "landlord_id", nullable = false, updatable = false)
    private UUID landlordId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "held_paise", nullable = false)
    private long heldPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PROPOSED;

    @Column(name = "landlord_note", length = 500)
    private String landlordNote;

    @Column(name = "tenant_note", length = 500)
    private String tenantNote;

    @Column(name = "proposed_at", nullable = false)
    private Instant proposedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_method", length = 16)
    private Payment.Method refundMethod;

    @Column(name = "refunded_on")
    private LocalDate refundedOn;

    @Column(name = "refund_reference", length = 200)
    private String refundReference;

    @Column(name = "settled_at")
    private Instant settledAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "deposit_deductions", joinColumns = @JoinColumn(name = "settlement_id"))
    @OrderColumn(name = "position")
    private List<Deduction> deductions = new ArrayList<>();

    protected DepositSettlement() {
    }

    DepositSettlement(UUID leaseId, UUID landlordId, UUID tenantId) {
        this.leaseId = leaseId;
        this.landlordId = landlordId;
        this.tenantId = tenantId;
    }

    /** A first proposal, or a revised one after the tenant asked about it. */
    void propose(long heldPaise, List<Deduction> lines, String note, Instant now) {
        this.heldPaise = heldPaise;
        this.deductions.clear();
        this.deductions.addAll(lines);
        this.landlordNote = note;
        this.tenantNote = null;
        this.respondedAt = null;
        this.proposedAt = now;
        this.status = Status.PROPOSED;
    }

    void query(String note, Instant now) {
        this.tenantNote = note;
        this.respondedAt = now;
        this.status = Status.QUERIED;
    }

    /** Agreed; with nothing left to give back, it is settled on the spot. */
    void accept(Instant now) {
        this.respondedAt = now;
        this.status = Status.ACCEPTED;
        if (refundPaise() == 0) {
            this.settledAt = now;
            this.status = Status.SETTLED;
        }
    }

    void refund(Payment.Method method, LocalDate on, String reference, Instant now) {
        this.refundMethod = method;
        this.refundedOn = on;
        this.refundReference = reference;
        this.settledAt = now;
        this.status = Status.SETTLED;
    }

    public long deductedPaise() {
        return deductions.stream().mapToLong(Deduction::amountPaise).sum();
    }

    public long refundPaise() {
        return heldPaise - deductedPaise();
    }

    public boolean isOpenToChange() {
        return status == Status.PROPOSED || status == Status.QUERIED;
    }

    public UUID getLeaseId() {
        return leaseId;
    }

    public UUID getLandlordId() {
        return landlordId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public long getHeldPaise() {
        return heldPaise;
    }

    public Status getStatus() {
        return status;
    }

    public String getLandlordNote() {
        return landlordNote;
    }

    public String getTenantNote() {
        return tenantNote;
    }

    public Instant getProposedAt() {
        return proposedAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public Payment.Method getRefundMethod() {
        return refundMethod;
    }

    public LocalDate getRefundedOn() {
        return refundedOn;
    }

    public String getRefundReference() {
        return refundReference;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public List<Deduction> getDeductions() {
        return List.copyOf(deductions);
    }
}
