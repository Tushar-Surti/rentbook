package com.rentbook.caretaker;

import com.rentbook.config.RentbookProperties;
import com.rentbook.lease.LeaseService;
import com.rentbook.maintenance.Ticket;
import com.rentbook.maintenance.TicketService;
import com.rentbook.notification.EmailSender;
import com.rentbook.notification.Notifier;
import com.rentbook.realtime.LiveEvents;
import com.rentbook.user.Role;
import com.rentbook.user.User;
import com.rentbook.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The invite email, and a caretaker hearing about requests on the properties they look after. */
@Component
class CaretakerNotifications {

    private static final Logger log = LoggerFactory.getLogger(CaretakerNotifications.class);

    private final EmailSender email;
    private final Caretakers caretakers;
    private final LeaseService leases;
    private final LiveEvents live;
    private final UserRepository users;
    private final Notifier notifier;
    private final RentbookProperties rentbook;

    CaretakerNotifications(EmailSender email, Caretakers caretakers, LeaseService leases, LiveEvents live,
                           UserRepository users, Notifier notifier, RentbookProperties rentbook) {
        this.email = email;
        this.caretakers = caretakers;
        this.leases = leases;
        this.live = live;
        this.users = users;
        this.notifier = notifier;
        this.rentbook = rentbook;
    }

    @Async
    @TransactionalEventListener
    void invited(Caretakers.CaretakerInvited invited) {
        try {
            email.send(invited.email(), invited.landlordName() + " added you as a caretaker on Rentbook",
                    """
                    Hi %s,

                    %s has asked you to look after some of their properties on Rentbook: to see who has paid, record rent paid to you in cash, and handle repair requests.

                    Open this link to set your password and start:
                    %s

                    The link works for 7 days.
                    """.formatted(invited.fullName(), invited.landlordName(), invited.link()));
        } catch (RuntimeException e) {
            // The landlord's page also shows the link to copy, so a failed email is logged, not fatal.
            log.warn("Caretaker invite email to {} failed", invited.email(), e);
        }
    }

    /** New requests and replies reach the caretakers of that property too, live; a new request also by email. */
    @TransactionalEventListener
    void requestChanged(TicketService.TicketChanged changed) {
        List<UUID> team = team(changed);
        if (team.isEmpty()) return;
        Map<String, Object> data = Map.of("ticketId", changed.ticketId(), "leaseId", changed.leaseId(),
                "title", changed.title(), "kind", changed.kind(), "actor", changed.actorName(),
                "status", changed.status().name());
        team.stream().filter(userId -> !userId.equals(changed.actorId()))
                .forEach(userId -> live.toUser(userId, "ticket." + changed.kind(), data));
    }

    @Async
    @TransactionalEventListener
    void newRequestEmail(TicketService.TicketChanged changed) {
        if (!"opened".equals(changed.kind())) return;
        String link = rentbook.appBaseUrl().toString().replaceAll("/+$", "") + "/c/requests/" + changed.ticketId();
        for (UUID userId : team(changed)) {
            users.findById(userId).filter(User::isActive).ifPresent(caretaker -> notifier.send(caretaker.getId(),
                    Notifier.Channel.EMAIL, "ticket.opened", "ticket:" + changed.ticketId() + ":opened:" + userId,
                    caretaker.getEmail(), new Notifier.Message(
                            (changed.priority() == Ticket.Priority.URGENT ? "Urgent request: " : "New request: ")
                                    + changed.title(),
                            "%s raised a maintenance request: \"%s\".%n%nRead it and reply here:%n%s%n"
                                    .formatted(changed.actorName(), changed.title(), link),
                            "Rentbook: request from %s: %s. %s".formatted(changed.actorName(), changed.title(), link))));
        }
    }

    private List<UUID> team(TicketService.TicketChanged changed) {
        UUID propertyId = leases.view(changed.leaseId(), changed.landlordId(), Role.LANDLORD).property().id();
        return caretakers.usersFor(propertyId);
    }
}
