package com.rentbook.lease;

import com.rentbook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** The single shared record between one landlord and one tenant for one unit. */
@Entity
@Table(name = "leases")
public class Lease extends BaseEntity {

    public enum Status { ACTIVE, NOTICE, ENDED }

    @Column(name = "unit_id", nullable = false, updatable = false)
    private UUID unitId;

    @Column(name = "landlord_id", nullable = false, updatable = false)
    private UUID landlordId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "invite_id", updatable = false)
    private UUID inviteId;

    @Column(name = "rent_paise", nullable = false)
    private long rentPaise;

    @Column(name = "deposit_paise", nullable = false)
    private long depositPaise;

    @Column(name = "due_day", nullable = false)
    private int dueDay;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.ACTIVE;

    protected Lease() {
    }

    public Lease(UUID unitId, UUID landlordId, UUID tenantId, UUID inviteId, long rentPaise, long depositPaise,
                 int dueDay, LocalDate startsOn, LocalDate endsOn) {
        this.unitId = unitId;
        this.landlordId = landlordId;
        this.tenantId = tenantId;
        this.inviteId = inviteId;
        this.rentPaise = rentPaise;
        this.depositPaise = depositPaise;
        this.dueDay = dueDay;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
    }

    /** Ends today or later; a future date puts the lease on notice until then. */
    void end(LocalDate endsOn, LocalDate today) {
        this.endsOn = endsOn;
        this.status = endsOn.isAfter(today) ? Status.NOTICE : Status.ENDED;
    }

    public boolean isLive() {
        return status != Status.ENDED;
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

    public long getRentPaise() {
        return rentPaise;
    }

    public long getDepositPaise() {
        return depositPaise;
    }

    public int getDueDay() {
        return dueDay;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public Status getStatus() {
        return status;
    }
}
