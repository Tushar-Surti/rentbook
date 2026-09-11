package com.rentbook.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LandlordProfileRepository extends JpaRepository<LandlordProfile, UUID> {
}
