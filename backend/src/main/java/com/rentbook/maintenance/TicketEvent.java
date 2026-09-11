package com.rentbook.maintenance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/** One line of a request's thread: something either party wrote, or a change of status. Never edited. */
@Entity
@Table(name = "ticket_events")
public class TicketEvent implements Persistable<UUID> {

    public enum Kind { MESSAGE, STATUS_CHANGE }

    @Id
    private UUID id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Kind kind;

    @Column(length = 4000, updatable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 16, updatable = false)
    private Ticket.Status fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 16, updatable = false)
    private Ticket.Status toStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected TicketEvent() {
    }

    private TicketEvent(UUID ticketId, UUID authorId, Kind kind, String body, Ticket.Status fromStatus,
                        Ticket.Status toStatus, Instant at) {
        this.id = UUID.randomUUID();
        this.ticketId = ticketId;
        this.authorId = authorId;
        this.kind = kind;
        this.body = body;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.createdAt = at;
    }

    static TicketEvent message(UUID ticketId, UUID authorId, String body, Instant at) {
        return new TicketEvent(ticketId, authorId, Kind.MESSAGE, body, null, null, at);
    }

    static TicketEvent statusChange(UUID ticketId, UUID authorId, Ticket.Status from, Ticket.Status to, Instant at) {
        return new TicketEvent(ticketId, authorId, Kind.STATUS_CHANGE, null, from, to, at);
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public Kind getKind() {
        return kind;
    }

    public String getBody() {
        return body;
    }

    public Ticket.Status getFromStatus() {
        return fromStatus;
    }

    public Ticket.Status getToStatus() {
        return toStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
