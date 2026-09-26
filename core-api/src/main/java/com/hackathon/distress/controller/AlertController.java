package com.hackathon.distress.controller;

import com.hackathon.distress.dto.AlertRequest;
import com.hackathon.distress.entity.Alert;
import com.hackathon.distress.service.AlertService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    // Dashboard: alert history
    @GetMapping
    public List<Alert> list() {
        return alertService.listAll();
    }

    // Analysis service: "something is wrong, send an alert"
    @PostMapping
    public ResponseEntity<Alert> create(@Valid @RequestBody AlertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alertService.create(request));
    }

    // Dashboard: "Send test alert" button
    @PostMapping("/test-alert")
    public Alert testAlert() {
        return alertService.createTestAlert();
    }

    // The link inside the notification. It's a GET so tapping the link works.
    @GetMapping(value = "/{id}/ack", produces = MediaType.TEXT_HTML_VALUE)
    public String acknowledge(@PathVariable Long id) {
        return alertService.acknowledge(id)
                .map(alert -> "CANCELLED".equals(alert.getStatus())
                        ? "<h2>Alert #" + id + " was a false alarm.</h2><p>No action needed.</p>"
                        : "<h2>Thanks, alert #" + id + " is acknowledged.</h2><p>Other contacts will not be notified.</p>")
                .orElse("<h2>Alert not found.</h2>");
    }

    // Dashboard "Mark false alarm" button, or the spoken cancel phrase
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Alert> cancel(@PathVariable Long id) {
        return alertService.cancel(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
