package com.hackathon.distress.repository;

import com.hackathon.distress.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * AlertRepository.java
 * ---------------------
 * `findByStatusAndLastNotifiedAtBefore` powers the EscalationService: it
 * finds every still-PENDING alert whose last notification is older than
 * the escalation timeout, so we know who to re-notify.
 */
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findAllByOrderByCreatedAtDesc();

    List<Alert> findByStatusAndLastNotifiedAtBefore(String status, Instant cutoff);
}
