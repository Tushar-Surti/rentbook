package com.rentbook.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Payment> findByLeaseIdOrderByCreatedAtDesc(UUID leaseId);

    Optional<Payment> findByRzpPaymentId(String rzpPaymentId);

    /** Webhook deliveries for one order are applied one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.rzpOrderId = :orderId")
    Optional<Payment> lockByOrderId(@Param("orderId") String orderId);

    @Query("select count(p) > 0 from Payment p join p.chargeIds c where c in :chargeIds and p.status = :status")
    boolean anyCoverWithStatus(@Param("chargeIds") Collection<UUID> chargeIds, @Param("status") Payment.Status status);

    @Query("""
            select count(p) > 0 from Payment p join p.chargeIds c
            where c in :chargeIds and p.status = :status and p.createdAt > :since""")
    boolean anyCoverWithStatusSince(@Param("chargeIds") Collection<UUID> chargeIds,
                                    @Param("status") Payment.Status status, @Param("since") Instant since);

    Optional<Payment> findFirstByLeaseIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(UUID leaseId,
                                                                                       Payment.Status status,
                                                                                       Instant since);
}
