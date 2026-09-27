package com.rentbook.listing;

import com.rentbook.config.RentbookProperties;
import com.rentbook.notification.Notifier;
import com.rentbook.realtime.LiveEvents;
import com.rentbook.user.UserRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** A new enquiry reaches the landlord live and by email, with the details to call back straight away. */
@Component
class ListingNotifications {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.of("en", "IN"));

    private final LiveEvents live;
    private final UserRepository users;
    private final Notifier notifier;
    private final RentbookProperties rentbook;

    ListingNotifications(LiveEvents live, UserRepository users, Notifier notifier, RentbookProperties rentbook) {
        this.live = live;
        this.users = users;
        this.notifier = notifier;
        this.rentbook = rentbook;
    }

    @TransactionalEventListener
    void announce(Listings.EnquiryReceived received) {
        live.toUser(received.landlordId(), "listing.enquiry", Map.of("listingId", received.listingId(),
                "name", received.name(), "place", received.place()));
    }

    @Async
    @TransactionalEventListener
    void email(Listings.EnquiryReceived received) {
        users.findById(received.landlordId()).ifPresent(landlord -> {
            String link = rentbook.appBaseUrl().toString().replaceAll("/+$", "") + "/l/listings/" + received.listingId();
            StringBuilder body = new StringBuilder()
                    .append("%s asked about %s.%n%n".formatted(received.name(), received.place()))
                    .append("Phone: %s%n".formatted(received.phone()));
            if (received.email() != null) body.append("Email: %s%n".formatted(received.email()));
            if (received.visitOn() != null) body.append("Would like to visit on %s%n".formatted(DAY.format(received.visitOn())));
            if (received.message() != null) body.append("%n\"%s\"%n".formatted(received.message()));
            body.append("%nSee every enquiry, and invite the one you choose:%n%s%n".formatted(link));
            notifier.send(landlord.getId(), Notifier.Channel.EMAIL, "listing.enquiry",
                    "enquiry:" + received.listingId() + ":" + UUID.randomUUID(), landlord.getEmail(),
                    new Notifier.Message("New enquiry for " + received.place() + " from " + received.name(),
                            body.toString(), "Rentbook: %s (%s) asked about %s.".formatted(received.name(),
                            received.phone(), received.place())));
        });
    }
}
