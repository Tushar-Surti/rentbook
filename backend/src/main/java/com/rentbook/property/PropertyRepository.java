package com.rentbook.property;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

    List<Property> findByLandlordIdOrderByName(UUID landlordId);

    Optional<Property> findByIdAndLandlordId(UUID id, UUID landlordId);
}
