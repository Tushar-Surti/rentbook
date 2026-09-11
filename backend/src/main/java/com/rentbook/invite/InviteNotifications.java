package com.rentbook.invite;

import com.rentbook.common.Rupees;
import com.rentbook.config.RentbookProperties;
import com.rentbook.property.PropertyRepository;
import com.rentbook.property.UnitRepository;
import com.rentbook.realtime.LiveEvents;
import com.rentbook.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** Side effects of the invite lifecycle, run only after the transaction commits. */
@Component
class InviteNotifications {

    private static final Logger log = LoggerFactory.getLogger(InviteNotifications.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final InviteRepository invites;
    private final UnitRepository units;
    private final PropertyRepository properties;
    private final UserRepository users;
    private final JavaMailSender mail;
    private final LiveEvents live;
    private final RentbookProperties rentbook;

    InviteNotifications(InviteRepository invites, UnitRepository units, PropertyRepository properties,
                        UserRepository users, JavaMailSender mail, LiveEvents live, RentbookProperties rentbook) {
        this.invites = invites;
        this.units = units;
        this.properties = properties;
        this.users = users;
        this.mail = mail;
        this.live = live;
        this.rentbook = rentbook;
    }

    @Async
    @TransactionalEventListener
    void emailInvite(InviteService.InviteIssued issued) {
        invites.findById(issued.inviteId()).ifPresent(invite -> {
            var unit = units.findById(invite.getUnitId()).orElseThrow();
            var property = properties.findById(unit.getPropertyId()).orElseThrow();
            String landlord = users.findById(invite.getLandlordId()).map(u -> u.getFullName()).orElse("Your landlord");

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(rentbook.mail().from());
            message.setTo(invite.getEmail());
            message.setSubject(landlord + " invited you to " + property.getName());
            message.setText("""
                    Hi %s,

                    %s has invited you to rent %s at %s, %s.

                    Rent: %s a month, due on day %d
                    Deposit: %s
                    Starts: %s

                    Accept the invite and set your password here:
                    %s

                    This link works once and expires on %s.
                    """.formatted(invite.getTenantName(), landlord, unit.getLabel(), property.getName(),
                    property.getCity(), Rupees.format(invite.getRentPaise()), invite.getDueDay(),
                    Rupees.format(invite.getDepositPaise()), DATE.format(invite.getStartsOn()), issued.link(),
                    DATE.format(invite.getExpiresAt().atZone(com.rentbook.common.IndiaTime.ZONE))));
            try {
                mail.send(message);
            } catch (MailException e) {
                log.warn("Invite email to {} failed; the landlord can still share the link", invite.getEmail(), e);
            }
        });
    }

    @TransactionalEventListener
    void announceMoveIn(InviteService.InviteAccepted accepted) {
        live.toUser(accepted.landlordId(), "invite.accepted", Map.of(
                "inviteId", accepted.inviteId(),
                "leaseId", accepted.leaseId(),
                "unitId", accepted.unitId(),
                "tenantName", accepted.tenantName()));
    }
}
