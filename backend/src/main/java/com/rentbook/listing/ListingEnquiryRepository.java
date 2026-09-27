package com.rentbook.listing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface ListingEnquiryRepository extends JpaRepository<ListingEnquiry, UUID> {

    List<ListingEnquiry> findByListingIdInOrderByCreatedAtDesc(Collection<UUID> listingIds);
}
