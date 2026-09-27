package com.rentbook.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface DepositSettlementRepository extends JpaRepository<DepositSettlement, UUID> {

    Optional<DepositSettlement> findByLeaseId(UUID leaseId);

    List<DepositSettlement> findByLeaseIdIn(Collection<UUID> leaseIds);
}
