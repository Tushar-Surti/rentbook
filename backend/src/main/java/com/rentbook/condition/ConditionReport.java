package com.rentbook.condition;

import com.rentbook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The walk-through of a home on the day a tenant moves in, or the day they leave. The landlord writes it as a
 * draft and sends it; the tenant adds their own notes and confirms it, and from then on it stays as it was.
 */
@Entity
@Table(name = "condition_reports")
public class ConditionReport extends BaseEntity {

    public enum Kind { MOVE_IN, MOVE_OUT }

    public enum Status { DRAFT, SENT, CONFIRMED }

    @Column(name = "lease_id", nullable = false, updatable = false)
    private UUID leaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.DRAFT;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected ConditionReport() {
    }

    ConditionReport(UUID leaseId, Kind kind) {
        this.leaseId = leaseId;
        this.kind = kind;
    }

    void send(Instant at) {
        this.status = Status.SENT;
        this.sentAt = at;
    }

    void confirm(Instant at) {
        this.status = Status.CONFIRMED;
        this.confirmedAt = at;
    }

    public boolean isDraft() {
        return status == Status.DRAFT;
    }

    public UUID getLeaseId() {
        return leaseId;
    }

    public Kind getKind() {
        return kind;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }
}
