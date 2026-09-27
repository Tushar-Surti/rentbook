package com.rentbook.listing;

import com.rentbook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A vacant unit offered publicly on a page of its own, until someone moves in or the landlord closes it. */
@Entity
@Table(name = "listings")
public class Listing extends BaseEntity {

    public enum Status { OPEN, CLOSED }

    @Column(name = "landlord_id", nullable = false, updatable = false)
    private UUID landlordId;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private UUID unitId;

    @Column(nullable = false, length = 16, updatable = false)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.OPEN;

    @Column(name = "rent_paise", nullable = false)
    private long rentPaise;

    @Column(name = "deposit_paise", nullable = false)
    private long depositPaise;

    @Column(name = "available_from", nullable = false)
    private LocalDate availableFrom;

    @Column(length = 2000)
    private String description;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected Listing() {
    }

    Listing(UUID landlordId, UUID unitId, String slug) {
        this.landlordId = landlordId;
        this.unitId = unitId;
        this.slug = slug;
    }

    void describe(long rentPaise, long depositPaise, LocalDate availableFrom, String description) {
        this.rentPaise = rentPaise;
        this.depositPaise = depositPaise;
        this.availableFrom = availableFrom;
        this.description = description;
    }

    void close(Instant now) {
        if (status == Status.OPEN) {
            status = Status.CLOSED;
            closedAt = now;
        }
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    public UUID getLandlordId() {
        return landlordId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public String getSlug() {
        return slug;
    }

    public Status getStatus() {
        return status;
    }

    public long getRentPaise() {
        return rentPaise;
    }

    public long getDepositPaise() {
        return depositPaise;
    }

    public LocalDate getAvailableFrom() {
        return availableFrom;
    }

    public String getDescription() {
        return description;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
