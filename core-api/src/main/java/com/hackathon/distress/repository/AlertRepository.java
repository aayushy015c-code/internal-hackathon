package com.hackathon.distress.repository;

import com.hackathon.distress.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// Spring writes the SQL for us based on the method names.
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findAllByOrderByCreatedAtDesc();

    // alerts that are still waiting and were last sent before `cutoff`
    List<Alert> findByStatusAndLastNotifiedAtBefore(String status, Instant cutoff);

    Optional<Alert> findByAckToken(String ackToken);

    @Modifying
    @Query("delete from Alert a where a.createdAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
