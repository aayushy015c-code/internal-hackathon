package com.hackathon.distress.controller;

import com.hackathon.distress.dto.AlertRequest;
import com.hackathon.distress.entity.Alert;
import com.hackathon.distress.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AlertController.java
 * ----------------------
 * REST endpoints for alerts. Two very different callers hit this class:
 *   - The analysis service (FastAPI) calls POST /api/alerts when it
 *     detects distress. This is server-to-server, both running on the
 *     same laptop during the hackathon.
 *   - The browser (dashboard, settings) calls GET /api/alerts, the
 *     cancel endpoint, and /test-alert.
 *   - A contact's phone/browser calls GET /api/alerts/{id}/ack when they
 *     tap "Acknowledge" inside the push notification - that's why ack is
 *     a GET (so it works as a plain clickable link) and returns a small
 *     HTML page instead of JSON.
 */
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public List<Alert> listAlerts() {
        return alertService.listAll();
    }

    /** Called by the analysis service when it detects a code word or a sustained stress signal. */
    @PostMapping
    public ResponseEntity<Alert> createAlert(@Valid @RequestBody AlertRequest request) {
        Alert alert = alertService.createAndDispatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(alert);
    }

    /** Sends a real notification to every contact right now, so the team can demo delivery without faking a call. */
    @PostMapping("/test-alert")
    public ResponseEntity<Alert> testAlert() {
        return ResponseEntity.ok(alertService.createTestAlert());
    }

    /** A contact taps "Acknowledge" in their ntfy notification - this is a GET so it works as a plain link. */
    @GetMapping(value = "/{id}/ack", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> acknowledge(@PathVariable Long id) {
        var updated = alertService.acknowledge(id);
        String html = updated.isPresent()
                ? "<html><body style='font-family:sans-serif;padding:2rem'><h2>Acknowledged.</h2>" +
                  "<p>Alert #" + id + " has been marked as acknowledged. No further escalation will happen.</p></body></html>"
                : "<html><body style='font-family:sans-serif;padding:2rem'><h2>Alert not found.</h2></body></html>";
        return ResponseEntity.ok(html);
    }

    /** The user marks the most recent alert as a false alarm from the dashboard. */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Alert> cancel(@PathVariable Long id) {
        return alertService.cancel(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
