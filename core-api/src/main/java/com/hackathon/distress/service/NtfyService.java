package com.hackathon.distress.service;

import com.hackathon.distress.entity.Alert;
import com.hackathon.distress.entity.Contact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * NtfyService.java
 * -----------------
 * Sends a push notification to one contact via ntfy.sh (a free, no-signup
 * pub/sub push service - you POST a message to a "topic" URL and anyone
 * subscribed to that topic on the ntfy app/website gets a push alert).
 *
 * IMPORTANT (spec requirement): "Notification sending must never block or
 * crash the analysis loop." We enforce that here with:
 *   1. A short connect/request timeout, so a slow network can't hang us.
 *   2. A try/catch around the whole send - on any failure we log it and
 *      return false, we never throw up into the caller.
 * The caller (AlertService) is responsible for recording that failure in
 * the alert's delivery log; it never crashes because of this class.
 */
@Slf4j
@Service
public class NtfyService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${ntfy.base-url}")
    private String ntfyBaseUrl;

    /**
     * Sends the given alert to one contact's ntfy topic.
     * Returns true if ntfy accepted the message (HTTP 2xx), false otherwise.
     * Never throws - all failures are caught and logged.
     */
    public boolean sendAlert(Contact contact, Alert alert, String ackUrl) {
        String title = "Distress alert - " + contact.getName();
        StringBuilder body = new StringBuilder();
        body.append("Trigger: ").append(alert.getTriggerPath()).append("\n");
        body.append("Time: ").append(alert.getCreatedAt()).append("\n");
        body.append("Reason: ").append(alert.getReasons()).append("\n");
        if (alert.getLatitude() != null && alert.getLongitude() != null) {
            body.append("Location: https://maps.google.com/?q=")
                    .append(alert.getLatitude()).append(",").append(alert.getLongitude()).append("\n");
        }
        body.append("Acknowledge: ").append(ackUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ntfyBaseUrl + "/" + contact.getNtfyTopic()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Title", title)
                    .header("Priority", "urgent")
                    .header("Tags", "warning")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            if (!ok) {
                log.warn("ntfy send to topic '{}' returned status {}", contact.getNtfyTopic(), response.statusCode());
            }
            return ok;
        } catch (Exception e) {
            // Never let a network problem crash the caller (the analysis/alert loop).
            log.warn("ntfy send to contact '{}' failed: {}", contact.getName(), e.getMessage());
            return false;
        }
    }
}
