package com.hackathon.distress.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * EscalationService.java
 * ------------------------
 * A background job (enabled by @EnableScheduling in DistressApplication)
 * that runs once a minute and asks AlertService to escalate any alert that
 * has been PENDING (un-acknowledged) for longer than the configured
 * timeout (default 2 minutes, see application.properties).
 *
 * Keeping this as its own tiny class - rather than folding the @Scheduled
 * method into AlertService - makes it obvious at a glance where the
 * "background timer" lives, which matters when a beginner is debugging
 * "why did contact #2 get notified?".
 */
@Service
@RequiredArgsConstructor
public class EscalationService {

    private final AlertService alertService;

    @Value("${app.escalation.timeout-minutes}")
    private long timeoutMinutes;

    @Scheduled(fixedRate = 60_000) // every 60 seconds
    public void checkOverdueAlerts() {
        Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);
        alertService.escalateOverdueAlerts(cutoff);
    }
}
