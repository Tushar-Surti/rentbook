package com.rentbook.property;

import com.rentbook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** A whole flat, a room, or a bed inside a room. A room that holds beds is let bed by bed. */
@Entity
@Table(name = "units")
public class Unit extends BaseEntity {

    public enum Kind { FLAT, ROOM, BED }

    public enum Status { VACANT, OCCUPIED, INACTIVE }

    @Column(name = "property_id", nullable = false, updatable = false)
    private UUID propertyId;

    @Column(name = "parent_unit_id", updatable = false)
    private UUID parentUnitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8, updatable = false)
    private Kind kind;

    @Column(nullable = false, length = 60)
    private String label;

    @Column(name = "default_rent_paise")
    private Long defaultRentPaise;

    @Column(name = "default_deposit_paise")
    private Long defaultDepositPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.VACANT;

    protected Unit() {
    }

    Unit(UUID propertyId, UUID parentUnitId, Kind kind, String label, Long defaultRentPaise, Long defaultDepositPaise) {
        this.propertyId = propertyId;
        this.parentUnitId = parentUnitId;
        this.kind = kind;
        update(label, defaultRentPaise, defaultDepositPaise);
    }

    void update(String label, Long defaultRentPaise, Long defaultDepositPaise) {
        this.label = label.strip();
        this.defaultRentPaise = defaultRentPaise;
        this.defaultDepositPaise = defaultDepositPaise;
    }

    void setActive(boolean active) {
        if (!active && status == Status.OCCUPIED) {
            throw new IllegalStateException("An occupied unit cannot be deactivated");
        }
        status = active ? (status == Status.INACTIVE ? Status.VACANT : status) : Status.INACTIVE;
    }

    public void markOccupied() {
        status = Status.OCCUPIED;
    }

    public void markVacant() {
        status = Status.VACANT;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public UUID getParentUnitId() {
        return parentUnitId;
    }

    public Kind getKind() {
        return kind;
    }

    public String getLabel() {
        return label;
    }

    public Long getDefaultRentPaise() {
        return defaultRentPaise;
    }

    public Long getDefaultDepositPaise() {
        return defaultDepositPaise;
    }

    public Status getStatus() {
        return status;
    }
}
