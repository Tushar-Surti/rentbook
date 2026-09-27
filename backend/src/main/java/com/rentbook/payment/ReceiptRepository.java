package com.rentbook.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    Optional<Receipt> findByPaymentId(UUID paymentId);

    @Query("""
            select r from Receipt r
            where r.paymentId in (select p.id from Payment p where p.leaseId = :leaseId)
            order by r.issuedAt desc, r.serialNo desc
            """)
    List<Receipt> findForLease(@Param("leaseId") UUID leaseId);
}
