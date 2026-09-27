package com.rentbook.listing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A photo in storage; PENDING until the bucket confirms it arrived. */
@Entity
@Table(name = "listing_photos")
class ListingPhoto {

    enum Status { PENDING, AVAILABLE }

    @Id
    private UUID id;

    @Column(name = "listing_id", nullable = false, updatable = false)
    private UUID listingId;

    @Column(name = "storage_key", nullable = false, updatable = false, length = 300)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ListingPhoto() {
    }

    ListingPhoto(UUID listingId, String contentType, String extension, Instant now) {
        this.id = UUID.randomUUID();
        this.listingId = listingId;
        this.contentType = contentType;
        this.storageKey = "listings/" + listingId + "/" + id + extension;
        this.createdAt = now;
    }

    void markAvailable(long size) {
        this.sizeBytes = size;
        this.status = Status.AVAILABLE;
    }

    UUID getId() {
        return id;
    }

    UUID getListingId() {
        return listingId;
    }

    String getStorageKey() {
        return storageKey;
    }

    String getContentType() {
        return contentType;
    }

    boolean isAvailable() {
        return status == Status.AVAILABLE;
    }
}
