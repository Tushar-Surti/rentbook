package com.rentbook.caretaker;

import com.rentbook.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Someone who looks after some of a landlord's properties: invited, then active, until removed. */
@Entity
@Table(name = "caretakers")
public class Caretaker extends BaseEntity {

    public enum Status { INVITED, ACTIVE, REMOVED }

    @Column(name = "landlord_id", nullable = false, updatable = false)
    private UUID landlordId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 254, updatable = false)
    private String email;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.INVITED;

    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "invite_expires_at")
    private Instant inviteExpiresAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "caretaker_properties", joinColumns = @JoinColumn(name = "caretaker_id"))
    @Column(name = "property_id", nullable = false)
    private Set<UUID> propertyIds = new HashSet<>();

    protected Caretaker() {
    }

    Caretaker(UUID landlordId, String fullName, String email, String phone, Collection<UUID> propertyIds,
              String tokenHash, Instant inviteExpiresAt) {
        this.landlordId = landlordId;
        this.fullName = fullName.strip();
        this.email = email;
        this.phone = phone;
        this.propertyIds.addAll(propertyIds);
        this.tokenHash = tokenHash;
        this.inviteExpiresAt = inviteExpiresAt;
    }

    void assign(Collection<UUID> properties) {
        propertyIds.clear();
        propertyIds.addAll(properties);
    }

    void reissue(String tokenHash, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.inviteExpiresAt = expiresAt;
    }

    void accept(UUID userId, String fullName) {
        this.userId = userId;
        this.fullName = fullName;
        this.status = Status.ACTIVE;
        this.tokenHash = null;
        this.inviteExpiresAt = null;
    }

    void remove() {
        this.status = Status.REMOVED;
        this.tokenHash = null;
        this.inviteExpiresAt = null;
    }

    public boolean isOpenInvite(Instant now) {
        return status == Status.INVITED && inviteExpiresAt != null && inviteExpiresAt.isAfter(now);
    }

    public UUID getLandlordId() {
        return landlordId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getInviteExpiresAt() {
        return inviteExpiresAt;
    }

    public Set<UUID> getPropertyIds() {
        return Set.copyOf(propertyIds);
    }
}
