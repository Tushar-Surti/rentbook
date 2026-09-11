package com.rentbook.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/** Issued once per confirmed payment and numbered per landlord. Immutable once written. */
@Entity
@Table(name = "receipts")
public class Receipt implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "landlord_id", nullable = false, updatable = false)
    private UUID landlordId;

    @Column(name = "serial_no", nullable = false, updatable = false)
    private int serialNo;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Transient
    private boolean isNew = true;

    protected Receipt() {
    }

    Receipt(UUID paymentId, UUID landlordId, int serialNo, Instant issuedAt) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.landlordId = landlordId;
        this.serialNo = serialNo;
        this.issuedAt = issuedAt;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    /** As printed on the receipt, e.g. "0007". */
    public String number() {
        return String.format("%04d", serialNo);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getLandlordId() {
        return landlordId;
    }

    public int getSerialNo() {
        return serialNo;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}
