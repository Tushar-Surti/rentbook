package com.rentbook.listing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface ListingPhotoRepository extends JpaRepository<ListingPhoto, UUID> {

    List<ListingPhoto> findByListingIdInOrderByCreatedAtAsc(Collection<UUID> listingIds);

    long countByListingId(UUID listingId);
}
