package com.rentbook.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PayoutAccountRepository extends JpaRepository<PayoutAccount, UUID> {
}
