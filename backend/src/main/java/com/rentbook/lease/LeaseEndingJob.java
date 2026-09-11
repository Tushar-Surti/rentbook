package com.rentbook.lease;

import com.rentbook.common.IndiaTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Closes leases on notice once their end date arrives, and frees their units. */
@Component
class LeaseEndingJob {

    private final LeaseService leases;
    private final Clock clock;

    LeaseEndingJob(LeaseService leases, Clock clock) {
        this.leases = leases;
        this.clock = clock;
    }

    @Scheduled(cron = "0 15 0 * * *", zone = "Asia/Kolkata")
    public void run() {
        leases.endLeasesDue(IndiaTime.today(clock));
    }
}
