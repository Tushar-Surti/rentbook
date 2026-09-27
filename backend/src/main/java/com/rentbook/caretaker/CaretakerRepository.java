package com.rentbook.caretaker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CaretakerRepository extends JpaRepository<Caretaker, UUID> {

    List<Caretaker> findByLandlordIdAndStatusNotOrderByCreatedAtAsc(UUID landlordId, Caretaker.Status status);

    Optional<Caretaker> findByIdAndLandlordId(UUID id, UUID landlordId);

    Optional<Caretaker> findByTokenHash(String tokenHash);

    Optional<Caretaker> findByUserId(UUID userId);

    @Query("""
            select c from Caretaker c join c.propertyIds p
            where p = :propertyId and c.status = com.rentbook.caretaker.Caretaker.Status.ACTIVE""")
    List<Caretaker> findActiveForProperty(@Param("propertyId") UUID propertyId);
}
