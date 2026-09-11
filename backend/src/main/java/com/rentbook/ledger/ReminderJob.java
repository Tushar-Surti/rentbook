package com.rentbook.ledger;

import com.rentbook.common.Rupees;
import com.rentbook.config.RentbookProperties;
import com.rentbook.lease.Lease;
import com.rentbook.lease.LeaseRepository;
import com.rentbook.notification.Notifier;
import com.rentbook.user.User;
import com.rentbook.user.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reminds tenants about unpaid charges three days before, on, and three days after the due date,
 * by email and, when a mobile number is on file, SMS. Each reminder goes out at most once.
 */
@Component
public class ReminderJob {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    enum Nudge {
        BEFORE(3, "rent.before"),
        DUE(0, "rent.due"),
        OVERDUE(-3, "rent.overdue");

        final int daysUntilDue;
        final String template;

        Nudge(int daysUntilDue, String template) {
            this.daysUntilDue = daysUntilDue;
            this.template = template;
        }
    }

    private final ChargeRepository charges;
    private final LeaseRepository leases;
    private final UserRepository users;
    private final Notifier notifier;
    private final LedgerService ledger;
    private final RentbookProperties properties;

    ReminderJob(ChargeRepository charges, LeaseRepository leases, UserRepository users, Notifier notifier,
                LedgerService ledger, RentbookProperties properties) {
        this.charges = charges;
        this.leases = leases;
        this.users = users;
        this.notifier = notifier;
        this.ledger = ledger;
        this.properties = properties;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
    public void runToday() {
        run(ledger.today());
    }

    /** Sends the reminders due on {@code today}; returns how many messages went out. */
    public int run(LocalDate today) {
        Map<LocalDate, Nudge> nudgeByDueDate = Arrays.stream(Nudge.values())
                .collect(Collectors.toMap(nudge -> today.plusDays(nudge.daysUntilDue), Function.identity()));
        List<Charge> open = charges.findByStatusAndDueOnIn(Charge.Status.DUE, nudgeByDueDate.keySet());
        if (open.isEmpty()) {
            return 0;
        }
        Map<UUID, Lease> leaseById = leases.findAllById(open.stream().map(Charge::getLeaseId).distinct().toList())
                .stream().collect(Collectors.toMap(Lease::getId, Function.identity()));
        Map<UUID, User> userById = users.findAllById(leaseById.values().stream()
                        .flatMap(lease -> Stream.of(lease.getTenantId(), lease.getLandlordId())).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        int sent = 0;
        for (Charge charge : open) {
            Lease lease = leaseById.get(charge.getLeaseId());
            if (lease == null || !lease.isLive()) {
                continue;
            }
            User tenant = userById.get(lease.getTenantId());
            String landlord = userById.get(lease.getLandlordId()).getFullName();
            Nudge nudge = nudgeByDueDate.get(charge.getDueOn());
            Notifier.Message message = message(nudge, charge, tenant, landlord);
            String key = "reminder:" + charge.getId() + ":" + nudge.template;
            if (notifier.send(tenant.getId(), Notifier.Channel.EMAIL, nudge.template, key + ":email",
                    tenant.getEmail(), message)) {
                sent++;
            }
            if (tenant.getPhone() != null && notifier.send(tenant.getId(), Notifier.Channel.SMS, nudge.template,
                    key + ":sms", tenant.getPhone(), message)) {
                sent++;
            }
        }
        return sent;
    }

    private Notifier.Message message(Nudge nudge, Charge charge, User tenant, String landlord) {
        String amount = Rupees.format(charge.getAmountPaise());
        String due = DAY.format(charge.getDueOn());
        String link = properties.appBaseUrl().toString().replaceAll("/+$", "") + "/t";
        String what = charge.getDescription().toLowerCase(Locale.ENGLISH);
        return switch (nudge) {
            case BEFORE -> new Notifier.Message(
                    charge.getDescription() + " due on " + due,
                    "Hi %s,%n%nYour %s of %s is due on %s.%n%nYour rent book: %s%n".formatted(
                            tenant.getFullName(), what, amount, due, link),
                    "Rentbook: %s of %s is due on %s. %s".formatted(charge.getDescription(), amount, due, link));
            case DUE -> new Notifier.Message(
                    "Rent due today",
                    "Hi %s,%n%nYour %s of %s is due today.%n%nYour rent book: %s%n".formatted(
                            tenant.getFullName(), what, amount, link),
                    "Rentbook: %s of %s is due today. %s".formatted(charge.getDescription(), amount, link));
            case OVERDUE -> new Notifier.Message(
                    "Rent overdue since " + due,
                    "Hi %s,%n%nYour %s of %s was due on %s and hasn't been paid yet. %s sees the same rent book.%n%nYour rent book: %s%n"
                            .formatted(tenant.getFullName(), what, amount, due, landlord, link),
                    "Rentbook: %s of %s was due on %s and is unpaid. %s".formatted(
                            charge.getDescription(), amount, due, link));
        };
    }
}
