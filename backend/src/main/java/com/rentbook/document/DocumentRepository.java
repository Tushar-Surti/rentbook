package com.rentbook.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndUploadedBy(UUID id, UUID uploadedBy);

    List<Document> findByTicketEventIdInAndStatusOrderByCreatedAtAsc(Collection<UUID> ticketEventIds,
                                                                     Document.Status status);

    List<Document> findByLeaseIdAndStatusAndTypeNotInOrderByCreatedAtDesc(UUID leaseId, Document.Status status,
                                                                           Collection<Document.Type> types);

    List<Document> findByConditionItemIdInAndStatusOrderByCreatedAtAsc(Collection<UUID> conditionItemIds,
                                                                      Document.Status status);

    List<Document> findByConditionItemIdIn(Collection<UUID> conditionItemIds);

    long countByConditionItemId(UUID conditionItemId);

    List<Document> findByStatusAndCreatedAtBefore(Document.Status status, Instant cutoff);
}
