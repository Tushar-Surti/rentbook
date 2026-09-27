package com.rentbook.listing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Someone who saw the page and wants the place, or at least a visit. */
@Entity
@Table(name = "listing_enquiries")
class ListingEnquiry {

    enum Status { NEW, INVITED, DISMISSED }

    @Id
    private UUID id;

    @Column(name = "listing_id", nullable = false, updatable = false)
    private UUID listingId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 254)
    private String email;

    @Column(length = 1000)
    private String message;

    @Column(name = "visit_on")
    private LocalDate visitOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.NEW;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ListingEnquiry() {
    }

    ListingEnquiry(UUID listingId, String name, String phone, String email, String message, LocalDate visitOn, Instant now) {
        this.id = UUID.randomUUID();
        this.listingId = listingId;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.message = message;
        this.visitOn = visitOn;
        this.createdAt = now;
    }

    void mark(Status status) {
        this.status = status;
    }

    UUID getId() {
        return id;
    }

    UUID getListingId() {
        return listingId;
    }

    String getName() {
        return name;
    }

    String getPhone() {
        return phone;
    }

    String getEmail() {
        return email;
    }

    String getMessage() {
        return message;
    }

    LocalDate getVisitOn() {
        return visitOn;
    }

    Status getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
