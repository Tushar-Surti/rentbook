package com.rentbook.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface RecurringChargeRepository extends JpaRepository<RecurringCharge, UUID> {

    List<RecurringCharge> findByLeaseIdOrderByCreatedAtAsc(UUID leaseId);
}
