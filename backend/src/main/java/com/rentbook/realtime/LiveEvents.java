package com.rentbook.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Pushes live updates to STOMP subscribers. Call only after commit (from a transactional event
 * listener) so nobody is told about a change that rolled back.
 */
@Component
public class LiveEvents {

    public record Envelope(String type, Instant at, Object data) {
    }

    private final SimpMessagingTemplate messaging;

    LiveEvents(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    /** To one user's private queue, {@code /user/queue/events}. The STOMP principal name is the user id. */
    public void toUser(UUID userId, String type, Object data) {
        messaging.convertAndSendToUser(userId.toString(), "/queue/events", new Envelope(type, Instant.now(), data));
    }

    /** To both parties of a lease, {@code /topic/leases/{id}}. */
    public void toLease(UUID leaseId, String type, Object data) {
        messaging.convertAndSend("/topic/leases/" + leaseId, new Envelope(type, Instant.now(), data));
    }

    /** To both parties of a maintenance request, {@code /topic/tickets/{id}}. */
    public void toTicket(UUID ticketId, String type, Object data) {
        messaging.convertAndSend("/topic/tickets/" + ticketId, new Envelope(type, Instant.now(), data));
    }
}
