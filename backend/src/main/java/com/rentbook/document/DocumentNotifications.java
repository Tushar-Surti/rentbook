package com.rentbook.document;

import com.rentbook.realtime.LiveEvents;
import com.rentbook.user.User;
import com.rentbook.user.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.UUID;

/** After a shared document reaches the shelf: both open copies of the lease refresh, and the other party hears who filed what. */
@Component
class DocumentNotifications {

    private final LiveEvents live;
    private final UserRepository users;

    DocumentNotifications(LiveEvents live, UserRepository users) {
        this.live = live;
        this.users = users;
    }

    @TransactionalEventListener
    void announce(DocumentService.DocumentFiled filed) {
        Map<String, Object> data = Map.of(
                "leaseId", filed.leaseId(),
                "documentId", filed.documentId(),
                "type", filed.type().name(),
                "filename", filed.filename(),
                "actor", users.findById(filed.uploaderId()).map(User::getFullName).orElse("Someone"));
        live.toLease(filed.leaseId(), "document.filed", data);
        UUID other = filed.uploaderId().equals(filed.tenantId()) ? filed.landlordId() : filed.tenantId();
        live.toUser(other, "document.filed", data);
    }
}
