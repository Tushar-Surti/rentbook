package com.rentbook.invite;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, UUID> {

    Optional<Invite> findByTokenHash(String tokenHash);

    /** Serializes concurrent accepts of the same link. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invite i where i.tokenHash = :tokenHash")
    Optional<Invite> lockByTokenHash(@Param("tokenHash") String tokenHash);

    Optional<Invite> findByIdAndLandlordId(UUID id, UUID landlordId);

    List<Invite> findByLandlordIdOrderByCreatedAtDesc(UUID landlordId);

    List<Invite> findByUnitIdAndStatus(UUID unitId, Invite.Status status);
}
