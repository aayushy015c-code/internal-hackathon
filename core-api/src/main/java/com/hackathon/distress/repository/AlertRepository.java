package com.hackathon.distress.repository;

import com.hackathon.distress.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

// Spring writes the SQL for us based on the method names.
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findAllByOrderByCreatedAtDesc();

    // alerts that are still waiting and were last sent before `cutoff`
    List<Alert> findByStatusAndLastNotifiedAtBefore(String status, Instant cutoff);
}
