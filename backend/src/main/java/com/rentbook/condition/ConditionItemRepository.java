package com.rentbook.condition;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ConditionItemRepository extends JpaRepository<ConditionItem, UUID> {

    List<ConditionItem> findByReportIdOrderByPositionAsc(UUID reportId);

    List<ConditionItem> findByReportIdInOrderByPositionAsc(Collection<UUID> reportIds);

    @Query("select coalesce(max(line.position), 0) from ConditionItem line where line.reportId = :reportId")
    int lastPosition(UUID reportId);
}
