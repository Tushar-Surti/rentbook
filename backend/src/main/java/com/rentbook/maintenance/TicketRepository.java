package com.rentbook.maintenance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByTenantIdOrderByLastActivityAtDesc(UUID tenantId);

    List<Ticket> findByLandlordIdOrderByLastActivityAtDesc(UUID landlordId);

    Optional<Ticket> findByIdAndLandlordId(UUID id, UUID landlordId);

    Optional<Ticket> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("select count(t) > 0 from Ticket t where t.id = :id and (t.landlordId = :userId or t.tenantId = :userId)")
    boolean isParty(@Param("id") UUID id, @Param("userId") UUID userId);
}
