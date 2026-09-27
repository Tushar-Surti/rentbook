package com.rentbook.payment;

import com.rentbook.common.Rupees;
import com.rentbook.config.RentbookProperties;
import com.rentbook.lease.LeaseService;
import com.rentbook.notification.Notifier;
import com.rentbook.realtime.LiveEvents;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** After a settlement changes: both screens update, and whoever must act next gets an email. */
@Component
class DepositNotifications {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.of("en", "IN"));

    private final LiveEvents live;
    private final Notifier notifier;
    private final RentbookProperties rentbook;

    DepositNotifications(LiveEvents live, Notifier notifier, RentbookProperties rentbook) {
        this.live = live;
        this.notifier = notifier;
        this.rentbook = rentbook;
    }

    @TransactionalEventListener
    void announce(DepositSettlements.DepositChanged changed) {
        live.toLease(changed.lease().id(), "deposit.changed", Map.of("leaseId", changed.lease().id(),
                "status", changed.settlement().status()));
    }

    @Async
    @TransactionalEventListener
    void email(DepositSettlements.DepositChanged changed) {
        LeaseService.LeaseView lease = changed.lease();
        DepositSettlements.SettlementView deposit = changed.settlement();
        String base = rentbook.appBaseUrl().toString().replaceAll("/+$", "");
        String tenantLink = base + "/t";
        String landlordLink = base + "/l/p/" + lease.property().id() + "/leases/" + lease.id();
        String held = Rupees.format(deposit.heldPaise());
        String back = Rupees.format(deposit.refundPaise());
        String deducted = Rupees.format(deposit.deductedPaise());
        String key = "deposit:" + lease.id() + ":" + deposit.proposedAt().toEpochMilli() + ":" + deposit.status();
        LeaseService.PersonRef tenant = lease.tenant();
        LeaseService.PersonRef landlord = lease.landlord();

        switch (deposit.status()) {
            case PROPOSED -> notifier.send(tenant.id(), Notifier.Channel.EMAIL, "deposit.proposed", key + ":email",
                    tenant.email(), new Notifier.Message("Your deposit settlement from " + landlord.fullName(),
                            "Hi %s,%n%n%s has settled your security deposit: of %s held, %s is deducted and %s comes back to you.%n%nReview it, then accept it or ask about it:%n%s%n"
                                    .formatted(tenant.fullName(), landlord.fullName(), held, deducted, back, tenantLink),
                            "Rentbook: your deposit settlement is ready, %s back of %s. %s".formatted(back, held, tenantLink)));
            case QUERIED -> notifier.send(landlord.id(), Notifier.Channel.EMAIL, "deposit.queried", key + ":email",
                    landlord.email(), new Notifier.Message(tenant.fullName() + " asked about the deposit settlement",
                            "Hi %s,%n%n%s asked about the deposit settlement:%n%n\"%s\"%n%nUpdate it and send it again:%n%s%n"
                                    .formatted(landlord.fullName(), tenant.fullName(), deposit.tenantNote(), landlordLink),
                            "Rentbook: %s asked about the deposit settlement. %s".formatted(tenant.fullName(), landlordLink)));
            case ACCEPTED -> notifier.send(landlord.id(), Notifier.Channel.EMAIL, "deposit.accepted", key + ":email",
                    landlord.email(), new Notifier.Message(tenant.fullName() + " accepted the deposit settlement",
                            "Hi %s,%n%n%s accepted the deposit settlement. Refund %s, then record it on the lease:%n%s%n"
                                    .formatted(landlord.fullName(), tenant.fullName(), back, landlordLink),
                            "Rentbook: %s accepted. Refund %s and record it. %s".formatted(tenant.fullName(), back, landlordLink)));
            case SETTLED -> {
                if (deposit.refund() == null) {
                    notifier.send(landlord.id(), Notifier.Channel.EMAIL, "deposit.settled", key + ":email",
                            landlord.email(), new Notifier.Message(tenant.fullName() + " accepted the deposit settlement",
                                    "Hi %s,%n%n%s accepted the deposit settlement. The whole %s went to the deductions, so there's nothing to refund.%n"
                                            .formatted(landlord.fullName(), tenant.fullName(), held),
                                    "Rentbook: %s accepted the deposit settlement.".formatted(tenant.fullName())));
                } else {
                    DepositSettlements.Refund refund = deposit.refund();
                    notifier.send(tenant.id(), Notifier.Channel.EMAIL, "deposit.refunded", key + ":email",
                            tenant.email(), new Notifier.Message("Your deposit refund of " + back,
                                    "Hi %s,%n%n%s has refunded %s of your deposit, paid %s on %s.%s%n%nThe settlement stays in your rent book:%n%s%n"
                                            .formatted(tenant.fullName(), landlord.fullName(), back, refund.method().phrase(),
                                                    DAY.format(refund.refundedOn()),
                                                    refund.reference() == null ? "" : " Reference: " + refund.reference() + ".",
                                                    tenantLink),
                                    "Rentbook: %s refunded %s of your deposit. %s".formatted(landlord.fullName(), back, tenantLink)));
                }
            }
        }
    }
}
