package com.rentbook.auth;

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

/**
 * One link in a rotation chain. Only the SHA-256 hash of the cookie value is stored. Every token
 * issued from one login shares a {@code familyId}, so reuse of a rotated token can revoke the chain.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected RefreshToken() {
    }

    RefreshToken(UUID userId, String tokenHash, UUID familyId, Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    boolean isUsable(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    boolean wasRotated() {
        return replacedById != null;
    }

    boolean rotatedWithin(java.time.Duration grace, Instant now) {
        return wasRotated() && revokedAt != null && now.isBefore(revokedAt.plus(grace));
    }

    void rotateTo(RefreshToken next, Instant now) {
        revokedAt = now;
        replacedById = next.id;
    }

    void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    UUID getUserId() {
        return userId;
    }

    UUID getFamilyId() {
        return familyId;
    }
}
