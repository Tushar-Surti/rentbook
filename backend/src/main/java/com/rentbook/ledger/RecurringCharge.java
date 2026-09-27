package com.rentbook.ledger;

import com.rentbook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/** A charge that comes round with the rent every month, from {@code startsMonth} until it is stopped. */
@Entity
@Table(name = "recurring_charges")
public class RecurringCharge extends BaseEntity {

    @Column(name = "lease_id", nullable = false, updatable = false)
    private UUID leaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Charge.Kind kind;

    @Column(nullable = false, length = 80, updatable = false)
    private String label;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Column(name = "starts_month", nullable = false, updatable = false)
    private LocalDate startsMonth;

    /** The last month it was billed for; null while it runs. */
    @Column(name = "ends_month")
    private LocalDate endsMonth;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    protected RecurringCharge() {
    }

    RecurringCharge(UUID leaseId, Charge.Kind kind, String label, long amountPaise, YearMonth startsMonth,
                    UUID createdBy) {
        this.leaseId = leaseId;
        this.kind = kind;
        this.label = label.strip();
        this.amountPaise = amountPaise;
        this.startsMonth = startsMonth.atDay(1);
        this.createdBy = createdBy;
    }

    /** Nothing after {@code lastMonth} is billed; months already on the book stay there. */
    void stopAfter(YearMonth lastMonth) {
        this.endsMonth = lastMonth.atDay(1);
    }

    public boolean runsIn(YearMonth period) {
        return !period.isBefore(YearMonth.from(startsMonth)) && (endsMonth == null || !period.isAfter(YearMonth.from(endsMonth)));
    }

    public boolean isRunning() {
        return endsMonth == null;
    }

    public UUID getLeaseId() {
        return leaseId;
    }

    public Charge.Kind getKind() {
        return kind;
    }

    public String getLabel() {
        return label;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public LocalDate getStartsMonth() {
        return startsMonth;
    }

    public LocalDate getEndsMonth() {
        return endsMonth;
    }
}
