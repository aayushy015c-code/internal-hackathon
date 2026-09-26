package com.hackathon.distress.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

// Sends a push notification using ntfy.sh (always over HTTPS).
// ntfy is simple: POST a message to https://ntfy.sh/<topic> and everyone
// subscribed to that topic in the ntfy app gets it.
@Service
public class NtfyService {

    private static final Logger log = LoggerFactory.getLogger(NtfyService.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${ntfy.base-url}")
    private String ntfyBaseUrl;

    // optional, for a protected / self-hosted ntfy server
    @Value("${ntfy.token:}")
    private String ntfyToken;

    // Returns true if ntfy accepted the message.
    // Never throws: a failed notification should not crash anything else.
    // buttonUrl can be null (no "Acknowledge" button).
    public boolean send(String topic, String title, String message, String buttonUrl) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(ntfyBaseUrl + "/" + URLEncoder.encode(topic, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(5))
                    .header("Title", title)
                    .header("Priority", "urgent")
                    .header("Tags", "warning")
                    .POST(HttpRequest.BodyPublishers.ofString(message, StandardCharsets.UTF_8));
            if (buttonUrl != null) {
                request.header("Actions", "view, Acknowledge, " + buttonUrl);
            }
            if (!ntfyToken.isBlank()) {
                request.header("Authorization", "Bearer " + ntfyToken);
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.warn("ntfy send failed: {}", e.getMessage());
            return false;
        }
    }
}
