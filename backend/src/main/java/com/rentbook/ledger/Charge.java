package com.rentbook.ledger;

import com.rentbook.common.ApiException;
import com.rentbook.common.BaseEntity;
import com.rentbook.lease.Lease;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** One line a tenant owes on a lease. Charges are never deleted: they are paid or waived. */
@Entity
@Table(name = "charges")
public class Charge extends BaseEntity {

    public enum Kind { RENT, DEPOSIT, UTILITY, OTHER }

    public enum Status { DUE, PAID, WAIVED }

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    @Column(name = "lease_id", nullable = false, updatable = false)
    private UUID leaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Kind kind;

    @Column(name = "period_month", updatable = false)
    private LocalDate periodMonth;

    @Column(nullable = false, length = 160)
    private String description;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.DUE;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "waived_at")
    private Instant waivedAt;

    @Column(name = "waived_by")
    private UUID waivedBy;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    /** Set on a month's line of a recurring add-on, so each month is made once. */
    @Column(name = "recurring_id", updatable = false)
    private UUID recurringId;

    @Column(name = "recurring_month", updatable = false)
    private LocalDate recurringMonth;

    protected Charge() {
    }

    private Charge(UUID leaseId, Kind kind, LocalDate periodMonth, String description, long amountPaise,
                   LocalDate dueOn, UUID createdBy) {
        this.leaseId = leaseId;
        this.kind = kind;
        this.periodMonth = periodMonth;
        this.description = description.strip();
        this.amountPaise = amountPaise;
        this.dueOn = dueOn;
        this.createdBy = createdBy;
    }

    static Charge rent(Lease lease, YearMonth period, LocalDate dueOn) {
        return new Charge(lease.getId(), Kind.RENT, period.atDay(1), "Rent for " + MONTH.format(period),
                lease.getRentPaise(), dueOn, null);
    }

    static Charge deposit(Lease lease) {
        return new Charge(lease.getId(), Kind.DEPOSIT, null, "Security deposit", lease.getDepositPaise(),
                lease.getStartsOn(), null);
    }

    /** One month of a recurring add-on: "Wi-Fi for October 2026", due with that month's rent. */
    static Charge addon(Lease lease, RecurringCharge addon, YearMonth period, LocalDate dueOn) {
        Charge charge = new Charge(lease.getId(), addon.getKind(), null, addon.getLabel() + " for " + MONTH.format(period),
                addon.getAmountPaise(), dueOn, null);
        charge.recurringId = addon.getId();
        charge.recurringMonth = period.atDay(1);
        return charge;
    }

    static Charge extra(Lease lease, Kind kind, String description, long amountPaise, LocalDate dueOn, UUID createdBy) {
        if (kind == Kind.RENT || kind == Kind.DEPOSIT) {
            throw ApiException.badRequest("charge_kind", "Rent and deposit are added automatically.");
        }
        return new Charge(lease.getId(), kind, null, description, amountPaise, dueOn, createdBy);
    }

    void waive(UUID landlordId, Instant now) {
        if (status != Status.DUE) {
            throw ApiException.conflict("charge_settled", "Only an unpaid charge can be waived.");
        }
        status = Status.WAIVED;
        waivedAt = now;
        waivedBy = landlordId;
    }

    /** Called when a verified payment settles this charge. */
    public void markPaid(Instant now) {
        status = Status.PAID;
        paidAt = now;
    }

    public boolean isOpen() {
        return status == Status.DUE;
    }

    public boolean isOverdue(LocalDate today) {
        return status == Status.DUE && dueOn.isBefore(today);
    }

    public UUID getLeaseId() {
        return leaseId;
    }

    public Kind getKind() {
        return kind;
    }

    public UUID getRecurringId() {
        return recurringId;
    }

    public LocalDate getRecurringMonth() {
        return recurringMonth;
    }

    public LocalDate getPeriodMonth() {
        return periodMonth;
    }

    public String getDescription() {
        return description;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public LocalDate getDueOn() {
        return dueOn;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getWaivedAt() {
        return waivedAt;
    }
}
