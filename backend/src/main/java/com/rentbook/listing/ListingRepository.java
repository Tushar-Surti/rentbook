package com.rentbook.listing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ListingRepository extends JpaRepository<Listing, UUID> {

    List<Listing> findByLandlordIdOrderByCreatedAtDesc(UUID landlordId);

    Optional<Listing> findByIdAndLandlordId(UUID id, UUID landlordId);

    Optional<Listing> findBySlug(String slug);

    Optional<Listing> findByUnitIdAndStatus(UUID unitId, Listing.Status status);

    List<Listing> findByLandlordIdAndStatus(UUID landlordId, Listing.Status status);
}
