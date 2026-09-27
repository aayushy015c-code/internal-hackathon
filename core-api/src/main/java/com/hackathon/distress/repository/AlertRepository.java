package com.hackathon.distress.repository;

import com.hackathon.distress.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// Spring writes the SQL for us based on the method names.
// Everything the user sees is filtered by userId.
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Alert> findByIdAndUserId(Long id, Long userId);

    // used by the escalation timer, for all users
    List<Alert> findByStatusAndLastNotifiedAtBefore(String status, Instant cutoff);

    Optional<Alert> findByAckTokenHash(String ackTokenHash);

    @Modifying
    @Query("delete from Alert a where a.createdAt < :cutoff")
    int deleteOlderThan(Instant cutoff);

    @Modifying
    @Query("delete from Alert a where a.userId = :userId")
    int deleteAllForUser(Long userId);

    // alerts saved before users existed belong to the first user
    @Modifying
    @Query("update Alert a set a.userId = :userId where a.userId is null")
    int claimUnowned(Long userId);
}
