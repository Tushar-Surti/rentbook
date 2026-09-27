package com.rentbook.condition;

import com.rentbook.config.RentbookProperties;
import com.rentbook.lease.LeaseService;
import com.rentbook.notification.Notifier;
import com.rentbook.realtime.LiveEvents;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/** A sent report reaches the tenant; a confirmed one tells the landlord. Both screens update live. */
@Component
class ConditionNotifications {

    private final LiveEvents live;
    private final Notifier notifier;
    private final RentbookProperties rentbook;

    ConditionNotifications(LiveEvents live, Notifier notifier, RentbookProperties rentbook) {
        this.live = live;
        this.notifier = notifier;
        this.rentbook = rentbook;
    }

    @TransactionalEventListener
    void announce(ConditionReports.ReportChanged changed) {
        live.toLease(changed.lease().id(), "condition.changed", Map.of("leaseId", changed.lease().id(),
                "reportId", changed.reportId(), "status", changed.status()));
    }

    @Async
    @TransactionalEventListener
    void email(ConditionReports.ReportChanged changed) {
        LeaseService.LeaseView lease = changed.lease();
        LeaseService.PersonRef tenant = lease.tenant();
        LeaseService.PersonRef landlord = lease.landlord();
        String base = rentbook.appBaseUrl().toString().replaceAll("/+$", "");
        String what = changed.kind() == ConditionReport.Kind.MOVE_IN ? "move-in" : "move-out";
        String home = lease.unit().roomLabel() == null ? lease.unit().label()
                : lease.unit().label() + ", " + lease.unit().roomLabel();
        String key = "condition:" + changed.reportId() + ":" + changed.status();
        switch (changed.status()) {
            case SENT -> {
                String link = base + "/t/condition/" + changed.reportId();
                notifier.send(tenant.id(), Notifier.Channel.EMAIL, "condition.sent", key + ":email", tenant.email(),
                        new Notifier.Message("Check the %s report for %s".formatted(what, home),
                                "Hi %s,%n%n%s has written up the %s condition of %s, %s, room by room with photos.%n%nRead it, add a note or a photo anywhere you see it differently, and confirm it:%n%s%n"
                                        .formatted(tenant.fullName(), landlord.fullName(), what, home,
                                                lease.property().name(), link),
                                "Rentbook: %s sent the %s report for %s. Check and confirm it: %s"
                                        .formatted(landlord.fullName(), what, home, link)));
            }
            case CONFIRMED -> {
                String link = base + "/l/p/" + lease.property().id() + "/leases/" + lease.id() + "/condition/"
                        + changed.reportId();
                String notes = changed.tenantNotes() == 0 ? "without adding any notes"
                        : changed.tenantNotes() == 1 ? "with a note on one line"
                        : "with notes on %d lines".formatted(changed.tenantNotes());
                notifier.send(landlord.id(), Notifier.Channel.EMAIL, "condition.confirmed", key + ":email",
                        landlord.email(), new Notifier.Message("%s confirmed the %s report".formatted(tenant.fullName(), what),
                                "Hi %s,%n%n%s confirmed the %s report for %s, %s, %s.%n%n%s%n"
                                        .formatted(landlord.fullName(), tenant.fullName(), what, home,
                                                lease.property().name(), notes, link),
                                "Rentbook: %s confirmed the %s report %s. %s"
                                        .formatted(tenant.fullName(), what, notes, link)));
            }
            case DRAFT -> {
            }
        }
    }
}
