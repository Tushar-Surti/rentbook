package com.rentbook.lease;

import com.rentbook.config.RentbookProperties;
import com.rentbook.notification.Notifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Tells the tenant when the landlord sets their last day, once that has committed. */
@Component
class LeaseNotifications {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.of("en", "IN"));

    private final Notifier notifier;
    private final RentbookProperties rentbook;

    LeaseNotifications(Notifier notifier, RentbookProperties rentbook) {
        this.notifier = notifier;
        this.rentbook = rentbook;
    }

    @Async
    @TransactionalEventListener
    void lastDaySet(LeaseService.LeaseEndSet event) {
        LeaseService.LeaseView lease = event.lease();
        String home = (lease.unit().roomLabel() == null
                ? lease.unit().label() : lease.unit().label() + ", " + lease.unit().roomLabel())
                + ", " + lease.property().name();
        String day = DAY.format(lease.endsOn());
        String landlord = lease.landlord().fullName();
        String link = rentbook.appBaseUrl().toString().replaceAll("/+$", "") + "/t";
        boolean ended = lease.status() == Lease.Status.ENDED;
        String subject = ended ? "Your lease at " + lease.property().name() + " has ended"
                : "Your move-out date: " + day;
        String body = ended
                ? "Hi %s,%n%n%s has ended your lease for %s, with %s as the last day.%n%nThank you for staying.%n"
                        .formatted(lease.tenant().fullName(), landlord, home, day)
                : "Hi %s,%n%n%s has set %s as your last day at %s. Rent stops after that, and your rent book stays open until then:%n%s%n"
                        .formatted(lease.tenant().fullName(), landlord, day, home, link);
        String sms = ended ? "Rentbook: %s has ended your lease for %s (last day %s).".formatted(landlord, home, day)
                : "Rentbook: %s has set %s as your last day at %s.".formatted(landlord, day, home);
        // One email per date: moving the date again tells the tenant again.
        notifier.send(lease.tenant().id(), Notifier.Channel.EMAIL, "lease.end",
                "lease-end:" + lease.id() + ":" + lease.endsOn() + ":" + lease.status() + ":email",
                lease.tenant().email(), new Notifier.Message(subject, body, sms));
    }
}
