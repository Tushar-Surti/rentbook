package com.rentbook.invite;

import com.rentbook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A landlord's offer of one unit on stated terms. The link carries a random token; only its hash is
 * stored, and resending replaces it so an older link stops working.
 */
@Entity
@Table(name = "invites")
public class Invite extends BaseEntity {

    public enum Status { PENDING, ACCEPTED, REVOKED, EXPIRED }

    @Column(name = "landlord_id", nullable = false, updatable = false)
    private UUID landlordId;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private UUID unitId;

    @Column(name = "tenant_name", nullable = false, length = 120)
    private String tenantName;

    @Column(nullable = false, length = 254, updatable = false)
    private String email;

    @Column(length = 16)
    private String phone;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "rent_paise", nullable = false, updatable = false)
    private long rentPaise;

    @Column(name = "deposit_paise", nullable = false, updatable = false)
    private long depositPaise;

    @Column(name = "due_day", nullable = false, updatable = false)
    private int dueDay;

    @Column(name = "starts_on", nullable = false, updatable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", updatable = false)
    private LocalDate endsOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_user_id")
    private UUID acceptedUserId;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected Invite() {
    }

    /** Joins a flat someone already lives in, for their own share of the rent. */
    @Column(nullable = false)
    private boolean flatmate;

    Invite(UUID landlordId, UUID unitId, String tenantName, String email, String phone, long rentPaise,
           long depositPaise, int dueDay, LocalDate startsOn, LocalDate endsOn, String tokenHash, Instant expiresAt) {
        this.landlordId = landlordId;
        this.unitId = unitId;
        this.tenantName = tenantName.strip();
        this.email = email;
        this.phone = phone;
        this.rentPaise = rentPaise;
        this.depositPaise = depositPaise;
        this.dueDay = dueDay;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isOpen(Instant now) {
        return status == Status.PENDING && now.isBefore(expiresAt);
    }

    /** What a reader should be told: a pending invite past its expiry reads as expired. */
    public Status effectiveStatus(Instant now) {
        return status == Status.PENDING && !now.isBefore(expiresAt) ? Status.EXPIRED : status;
    }

    void reissue(String tokenHash, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    void revoke() {
        status = Status.REVOKED;
    }

    void expire() {
        status = Status.EXPIRED;
    }

    void accept(UUID userId, Instant now) {
        status = Status.ACCEPTED;
        acceptedUserId = userId;
        acceptedAt = now;
    }

    public UUID getLandlordId() {
        return landlordId;
    }

    void markFlatmate() {
        this.flatmate = true;
    }

    public boolean isFlatmate() {
        return flatmate;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public String getTenantName() {
        return tenantName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
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

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
