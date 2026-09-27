package com.hackathon.distress.controller;

import com.hackathon.distress.dto.AlertRequest;
import com.hackathon.distress.entity.Alert;
import com.hackathon.distress.service.AlertService;
import com.hackathon.distress.service.UserService;
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
    private final UserService users;

    public AlertController(AlertService alertService, UserService users) {
        this.alertService = alertService;
        this.users = users;
    }

    // Dashboard: alert history
    @GetMapping
    public List<Alert> list(@RequestHeader(value = UserService.HEADER, required = false) String key) {
        return alertService.listAll(users.require(key).getId());
    }

    // Analysis service: "something is wrong, send an alert"
    @PostMapping
    public ResponseEntity<Alert> create(@RequestHeader(value = UserService.HEADER, required = false) String key, @Valid @RequestBody AlertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alertService.create(users.require(key).getId(), request));
    }

    // Dashboard: "Send test alert" button
    @PostMapping("/test-alert")
    public Alert testAlert(@RequestHeader(value = UserService.HEADER, required = false) String key) {
        return alertService.createTestAlert(users.require(key).getId());
    }

    // The link inside the notification. It's a GET so tapping the link works.
    // The token is random, so knowing the alert number isn't enough.
    @GetMapping(value = "/ack/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> acknowledge(@PathVariable String token) {
        return alertService.acknowledge(token)
                .map(alert -> ResponseEntity.ok("CANCELLED".equals(alert.getStatus())
                        ? "<h2>This alert was a false alarm.</h2><p>No action needed.</p>"
                        : "<h2>Thanks, the alert is acknowledged.</h2><p>Other contacts will not be notified.</p>"))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body("<h2>Alert not found.</h2>"));
    }

    // Dashboard "False alarm" button, or the spoken cancel phrase
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Alert> cancel(@RequestHeader(value = UserService.HEADER, required = false) String key, @PathVariable Long id) {
        return alertService.cancel(users.require(key).getId(), id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
