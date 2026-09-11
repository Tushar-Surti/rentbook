package com.rentbook.payment;

import com.rentbook.common.Rupees;
import com.rentbook.config.RentbookProperties;
import com.rentbook.notification.Notifier;
import com.rentbook.realtime.LiveEvents;
import com.rentbook.user.User;
import com.rentbook.user.UserRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/** What happens after a payment is confirmed or fails, once that has committed. */
@Component
class PaymentNotifications {

    private final LiveEvents live;
    private final UserRepository users;
    private final Notifier notifier;
    private final RentbookProperties rentbook;

    PaymentNotifications(LiveEvents live, UserRepository users, Notifier notifier, RentbookProperties rentbook) {
        this.live = live;
        this.users = users;
        this.notifier = notifier;
        this.rentbook = rentbook;
    }

    /**
     * Both readers of the book hear it at once: the tenant's slip takes its stamp, and the landlord's
     * register row flips on whichever page of the book they have open.
     */
    @TransactionalEventListener
    void announce(PaymentConfirmation.PaymentConfirmed confirmed) {
        Map<String, Object> data = new HashMap<>();
        data.put("leaseId", confirmed.leaseId());
        data.put("paymentId", confirmed.paymentId());
        data.put("receiptId", confirmed.receiptId());
        data.put("receiptNumber", confirmed.receiptNumber());
        data.put("amountPaise", confirmed.amountPaise());
        live.toLease(confirmed.leaseId(), "payment.confirmed", data);

        Map<String, Object> forLandlord = new HashMap<>(data);
        forLandlord.put("tenantName", users.findById(confirmed.tenantId()).map(User::getFullName).orElse("Your tenant"));
        live.toUser(confirmed.landlordId(), "payment.confirmed", forLandlord);
    }

    @Async
    @TransactionalEventListener
    void emailReceipt(PaymentConfirmation.PaymentConfirmed confirmed) {
        users.findById(confirmed.tenantId()).ifPresent(tenant -> {
            String amount = Rupees.format(confirmed.amountPaise());
            String link = rentbook.appBaseUrl().toString().replaceAll("/+$", "") + "/t/rent";
            notifier.send(tenant.getId(), Notifier.Channel.EMAIL, "payment.receipt",
                    "receipt:" + confirmed.receiptId() + ":email", tenant.getEmail(), new Notifier.Message(
                            "Payment of " + amount + " confirmed, receipt " + confirmed.receiptNumber(),
                            "Hi %s,%n%nRazorpay has confirmed your payment of %s. Receipt %s is in your rent book:%n%s%n"
                                    .formatted(tenant.getFullName(), amount, confirmed.receiptNumber(), link),
                            "Rentbook: payment of %s confirmed. Receipt %s: %s"
                                    .formatted(amount, confirmed.receiptNumber(), link)));
        });
    }

    @TransactionalEventListener
    void failed(PaymentConfirmation.PaymentFailed failed) {
        Map<String, Object> data = new HashMap<>();
        data.put("paymentId", failed.paymentId());
        data.put("reason", failed.reason());
        live.toUser(failed.tenantId(), "payment.failed", data);
    }
}
