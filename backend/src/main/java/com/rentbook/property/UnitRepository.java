package com.rentbook.property;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnitRepository extends JpaRepository<Unit, UUID> {

    List<Unit> findByPropertyIdInOrderByLabel(Collection<UUID> propertyIds);

    boolean existsByParentUnitId(UUID parentUnitId);

    @Query("""
            select u from Unit u
            where u.id = :unitId
              and u.propertyId in (select p.id from Property p where p.landlordId = :landlordId)
            """)
    Optional<Unit> findOwned(@Param("unitId") UUID unitId, @Param("landlordId") UUID landlordId);

    @Query("""
            select count(u) > 0 from Unit u
            where u.propertyId = :propertyId
              and ((:parentId is null and u.parentUnitId is null) or u.parentUnitId = :parentId)
              and lower(u.label) = lower(:label)
            """)
    boolean labelTaken(@Param("propertyId") UUID propertyId, @Param("parentId") UUID parentId,
                       @Param("label") String label);
}
