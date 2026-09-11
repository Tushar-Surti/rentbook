package com.rentbook.maintenance;

import com.rentbook.common.BaseEntity;
import com.rentbook.lease.Lease;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A maintenance request on one lease. Its story lives in the append-only thread of {@link TicketEvent}s. */
@Entity
@Table(name = "tickets")
public class Ticket extends BaseEntity {

    public enum Category { PLUMBING, ELECTRICAL, APPLIANCE, FURNITURE, CLEANING, PESTS, INTERNET, OTHER }

    public enum Priority { LOW, NORMAL, URGENT }

    public enum Status { OPEN, ACKNOWLEDGED, IN_PROGRESS, RESOLVED, CLOSED }

    @Column(name = "lease_id", nullable = false, updatable = false)
    private UUID leaseId;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private UUID unitId;

    @Column(name = "landlord_id", nullable = false, updatable = false)
    private UUID landlordId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 140)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.OPEN;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    protected Ticket() {
    }

    Ticket(Lease lease, String title, Category category, Priority priority, Instant openedAt) {
        this.leaseId = lease.getId();
        this.unitId = lease.getUnitId();
        this.landlordId = lease.getLandlordId();
        this.tenantId = lease.getTenantId();
        this.title = title;
        this.category = category;
        this.priority = priority;
        this.lastActivityAt = openedAt;
    }

    void touch(Instant at) {
        lastActivityAt = at;
    }

    void move(Status to, Instant at) {
        status = to;
        lastActivityAt = at;
    }

    /** Still needs someone: not yet resolved or closed. */
    public boolean isOpen() {
        return status != Status.RESOLVED && status != Status.CLOSED;
    }

    public UUID getLeaseId() {
        return leaseId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public UUID getLandlordId() {
        return landlordId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getTitle() {
        return title;
    }

    public Category getCategory() {
        return category;
    }

    public Priority getPriority() {
        return priority;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }
}
