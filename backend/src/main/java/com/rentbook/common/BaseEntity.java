package com.rentbook.common;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Identity, audit timestamps and optimistic locking shared by every aggregate.
 * Ids are assigned in Java so they exist before the insert; a null version marks the entity as new.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    private UUID id;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BaseEntity() {
        this(UUID.randomUUID());
    }

    protected BaseEntity(UUID id) {
        this.id = Objects.requireNonNull(id);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public final boolean equals(Object other) {
        return this == other || (other instanceof BaseEntity entity
                && getClass() == entity.getClass() && id.equals(entity.id));
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }
}
