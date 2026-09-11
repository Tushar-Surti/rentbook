package com.rentbook.document;

import com.rentbook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A file in the bucket and who may see it. The bytes live only in storage; this row holds where they
 * are, whose they are, and whether they have actually arrived.
 */
@Entity
@Table(name = "documents")
public class Document extends BaseEntity {

    public enum Type { LEASE, KYC, RECEIPT, TICKET_PHOTO, OTHER }

    public enum Visibility { LANDLORD_ONLY, LEASE_PARTIES }

    public enum Status { PENDING, AVAILABLE }

    @Column(name = "landlord_id", nullable = false, updatable = false)
    private UUID landlordId;

    @Column(name = "lease_id", updatable = false)
    private UUID leaseId;

    @Column(name = "ticket_event_id")
    private UUID ticketEventId;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Visibility visibility;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 300)
    private String storageKey;

    @Column(nullable = false, length = 200)
    private String filename;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    protected Document() {
    }

    /** The storage key is the lease and this document's own id; the uploader's filename never reaches it. */
    Document(UUID landlordId, UUID leaseId, UUID uploadedBy, Type type, Visibility visibility, String filename,
             String contentType, String extension) {
        this.landlordId = landlordId;
        this.leaseId = leaseId;
        this.uploadedBy = uploadedBy;
        this.type = type;
        this.visibility = visibility;
        this.filename = filename;
        this.contentType = contentType;
        this.storageKey = "leases/" + leaseId + "/" + getId() + extension;
    }

    void markAvailable(long size) {
        this.sizeBytes = size;
        this.status = Status.AVAILABLE;
    }

    void attachTo(UUID ticketEventId) {
        this.ticketEventId = ticketEventId;
    }

    public boolean isAvailable() {
        return status == Status.AVAILABLE;
    }

    public UUID getLandlordId() {
        return landlordId;
    }

    public UUID getLeaseId() {
        return leaseId;
    }

    public UUID getTicketEventId() {
        return ticketEventId;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Type getType() {
        return type;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public Status getStatus() {
        return status;
    }
}
