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

    List<Document> findByLeaseIdAndStatusAndTypeNotOrderByCreatedAtDesc(UUID leaseId, Document.Status status,
                                                                         Document.Type type);

    List<Document> findByStatusAndCreatedAtBefore(Document.Status status, Instant cutoff);
}
