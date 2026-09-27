package com.rentbook.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargeRepository extends JpaRepository<Charge, UUID> {

    List<Charge> findByLeaseIdOrderByDueOnAscCreatedAtAsc(UUID leaseId);

    List<Charge> findByLeaseIdInAndStatus(Collection<UUID> leaseIds, Charge.Status status);

    List<Charge> findByLeaseIdInAndKindAndPeriodMonth(Collection<UUID> leaseIds, Charge.Kind kind,
                                                      LocalDate periodMonth);

    List<Charge> findByStatusAndDueOnIn(Charge.Status status, Collection<LocalDate> dueDates);

    boolean existsByLeaseIdAndKindAndPeriodMonth(UUID leaseId, Charge.Kind kind, LocalDate periodMonth);

    boolean existsByRecurringIdAndRecurringMonth(UUID recurringId, LocalDate recurringMonth);

    java.util.Optional<Charge> findTopByRecurringIdOrderByRecurringMonthDesc(UUID recurringId);

    boolean existsByLeaseIdAndKind(UUID leaseId, Charge.Kind kind);

    Optional<Charge> findTopByLeaseIdAndKindOrderByPeriodMonthDesc(UUID leaseId, Charge.Kind kind);
}
