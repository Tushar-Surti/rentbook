package com.rentbook.ledger;

import com.rentbook.realtime.LiveEvents;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/** Tells both parties of a lease what changed in its ledger, and who changed it, once the change commits. */
@Component
class LedgerLiveEvents {

    private final LiveEvents live;

    LedgerLiveEvents(LiveEvents live) {
        this.live = live;
    }

    @TransactionalEventListener
    void relay(LedgerService.LedgerChanged changed) {
        Map<String, Object> data = new HashMap<>();
        data.put("leaseId", changed.leaseId());
        data.put("description", changed.description());
        data.put("amountPaise", changed.amountPaise());
        data.put("actor", changed.actor());
        live.toLease(changed.leaseId(), changed.type(), data);
    }
}
