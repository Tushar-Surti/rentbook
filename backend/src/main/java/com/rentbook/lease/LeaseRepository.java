package com.rentbook.lease;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaseRepository extends JpaRepository<Lease, UUID> {

    Optional<Lease> findByIdAndLandlordId(UUID id, UUID landlordId);

    Optional<Lease> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Lease> findByLandlordIdOrderByStartsOnDesc(UUID landlordId);

    List<Lease> findByTenantIdOrderByStartsOnDesc(UUID tenantId);

    boolean existsByIdAndLandlordId(UUID id, UUID landlordId);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    List<Lease> findByStatusIn(Collection<Lease.Status> statuses);

    List<Lease> findByStatusAndEndsOnLessThanEqual(Lease.Status status, LocalDate date);
}
