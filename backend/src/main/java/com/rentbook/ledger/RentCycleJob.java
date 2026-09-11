package com.rentbook.ledger;

import com.rentbook.lease.Lease;
import com.rentbook.lease.LeaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Makes sure every live lease has its rent charges, shortly after midnight in India and at startup.
 * Each lease is its own transaction; the unique index turns a race with another instance into a no-op.
 */
@Component
class RentCycleJob {

    private static final Logger log = LoggerFactory.getLogger(RentCycleJob.class);

    private final LeaseRepository leases;
    private final LedgerService ledger;

    RentCycleJob(LeaseRepository leases, LedgerService ledger) {
        this.leases = leases;
        this.ledger = ledger;
    }

    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Kolkata")
    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        LocalDate today = ledger.today();
        int created = 0;
        for (Lease lease : leases.findByStatusIn(List.of(Lease.Status.ACTIVE, Lease.Status.NOTICE))) {
            try {
                created += ledger.syncRent(lease, today);
            } catch (DataIntegrityViolationException raced) {
                log.debug("Rent for lease {} was generated concurrently", lease.getId());
            } catch (RuntimeException e) {
                log.warn("Could not generate rent for lease {}", lease.getId(), e);
            }
        }
        if (created > 0) {
            log.info("Generated {} rent charges", created);
        }
    }
}
