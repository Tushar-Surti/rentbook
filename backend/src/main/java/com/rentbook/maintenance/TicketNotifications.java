package com.rentbook.maintenance;

import com.rentbook.config.RentbookProperties;
import com.rentbook.notification.Notifier;
import com.rentbook.realtime.LiveEvents;
import com.rentbook.user.UserRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.UUID;

/**
 * After a request changes and the change has committed: both parties' screens hear it live, and the
 * two moments that matter away from the screen go out by email (and by SMS when it's urgent).
 */
@Component
class TicketNotifications {

    private final LiveEvents live;
    private final UserRepository users;
    private final Notifier notifier;
    private final RentbookProperties rentbook;

    TicketNotifications(LiveEvents live, UserRepository users, Notifier notifier, RentbookProperties rentbook) {
        this.live = live;
        this.users = users;
        this.notifier = notifier;
        this.rentbook = rentbook;
    }

    @TransactionalEventListener
    void announce(TicketService.TicketChanged changed) {
        Map<String, Object> data = Map.of(
                "ticketId", changed.ticketId(),
                "leaseId", changed.leaseId(),
                "title", changed.title(),
                "kind", changed.kind(),
                "actor", changed.actorName(),
                "status", changed.status().name());
        live.toTicket(changed.ticketId(), "ticket." + changed.kind(), data);
        UUID other = changed.actorId().equals(changed.tenantId()) ? changed.landlordId() : changed.tenantId();
        live.toUser(other, "ticket." + changed.kind(), data);
    }

    @Async
    @TransactionalEventListener
    void notifyAway(TicketService.TicketChanged changed) {
        if ("opened".equals(changed.kind())) {
            users.findById(changed.landlordId()).ifPresent(landlord -> {
                String link = link("/l/requests/" + changed.ticketId());
                String subject = (changed.priority() == Ticket.Priority.URGENT ? "Urgent request: " : "New request: ")
                        + changed.title();
                Notifier.Message message = new Notifier.Message(subject,
                        "%s raised a maintenance request: \"%s\".%n%nRead it and reply here:%n%s%n"
                                .formatted(changed.actorName(), changed.title(), link),
                        "Rentbook: urgent request from %s: %s. %s".formatted(changed.actorName(), changed.title(), link));
                notifier.send(landlord.getId(), Notifier.Channel.EMAIL, "ticket.opened",
                        "ticket:" + changed.ticketId() + ":opened:email", landlord.getEmail(), message);
                if (changed.priority() == Ticket.Priority.URGENT && landlord.getPhone() != null) {
                    notifier.send(landlord.getId(), Notifier.Channel.SMS, "ticket.opened",
                            "ticket:" + changed.ticketId() + ":opened:sms", landlord.getPhone(), message);
                }
            });
        } else if ("status".equals(changed.kind()) && changed.status() == Ticket.Status.RESOLVED) {
            users.findById(changed.tenantId()).ifPresent(tenant -> {
                String link = link("/t/requests/" + changed.ticketId());
                notifier.send(tenant.getId(), Notifier.Channel.EMAIL, "ticket.resolved",
                        "ticket:" + changed.ticketId() + ":resolved:" + changed.actorId() + ":" + System.nanoTime(),
                        tenant.getEmail(), new Notifier.Message(
                                changed.actorName() + " marked \"" + changed.title() + "\" resolved",
                                "Hi %s,%n%n%s has marked your request \"%s\" as resolved. If it isn't fixed, reopen it here:%n%s%n"
                                        .formatted(tenant.getFullName(), changed.actorName(), changed.title(), link),
                                "Rentbook: \"%s\" marked resolved. %s".formatted(changed.title(), link)));
            });
        }
    }

    private String link(String path) {
        return rentbook.appBaseUrl().toString().replaceAll("/+$", "") + path;
    }
}
