package com.rentbook.condition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConditionReportRepository extends JpaRepository<ConditionReport, UUID> {

    List<ConditionReport> findByLeaseIdOrderByCreatedAtAsc(UUID leaseId);

    Optional<ConditionReport> findByLeaseIdAndKind(UUID leaseId, ConditionReport.Kind kind);
}
