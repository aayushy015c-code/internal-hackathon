package com.hackathon.distress.service;

import com.hackathon.distress.dto.AlertRequest;
import com.hackathon.distress.entity.Alert;
import com.hackathon.distress.entity.AppConfig;
import com.hackathon.distress.entity.Contact;
import com.hackathon.distress.repository.AlertRepository;
import com.hackathon.distress.repository.AppConfigRepository;
import com.hackathon.distress.repository.ContactRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

// All the alert logic lives here:
//   1. create an alert and notify the first contact
//   2. every minute, if nobody acknowledged, notify the next contact
//   3. acknowledge / cancel an alert
@Service
public class AlertService {

    private final AlertRepository alertRepo;
    private final ContactRepository contactRepo;
    private final AppConfigRepository configRepo;
    private final NtfyService ntfy;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.escalation.timeout-minutes}")
    private long timeoutMinutes;

    public AlertService(AlertRepository alertRepo, ContactRepository contactRepo,
                        AppConfigRepository configRepo, NtfyService ntfy) {
        this.alertRepo = alertRepo;
        this.contactRepo = contactRepo;
        this.configRepo = configRepo;
        this.ntfy = ntfy;
    }

    public Alert create(AlertRequest req) {
        Alert alert = new Alert();
        alert.setTriggerPath(req.triggerPath());
        alert.setStressScore(req.stressScore());
        alert.setRollingScore(req.rollingScore());
        alert.setReasons(req.reasons());
        alert.setTranscriptSnippet(req.transcriptSnippet());
        alert.setLatitude(req.latitude());
        alert.setLongitude(req.longitude());
        alert = alertRepo.save(alert); // save first so we have an id for the ack link

        notifyContact(alert, 0);
        return alert;
    }

    public Alert createTestAlert() {
        return create(new AlertRequest("manual-test", "manual_test", 0, 0,
                "Test alert sent from the dashboard.", "", null, null));
    }

    // Sends the alert to the contact at position `stage` in the priority list.
    private void notifyContact(Alert alert, int stage) {
        List<Contact> contacts = contactRepo.findAllByOrderByPriorityOrderAsc();
        if (contacts.isEmpty()) {
            addToLog(alert, "no contacts saved, nothing sent");
            alertRepo.save(alert);
            return;
        }

        Contact contact = contacts.get(stage);
        String message = "Reason: " + alert.getReasons() + "\n";
        if (alert.getLatitude() != null && alert.getLongitude() != null) {
            message += "Location: https://maps.google.com/?q=" + alert.getLatitude() + "," + alert.getLongitude() + "\n";
        }
        message += "Tap to acknowledge: " + baseUrl + "/api/alerts/" + alert.getId() + "/ack";

        boolean sent = ntfy.send(contact.getNtfyTopic(), "Silent Signal alert", message);

        addToLog(alert, contact.getName() + ": " + (sent ? "sent" : "FAILED"));
        alert.setEscalationStage(stage);
        alert.setLastNotifiedAt(Instant.now());
        alertRepo.save(alert);
    }

    // Runs every 60 seconds. If an alert is still PENDING after the timeout,
    // send it to the next contact in the list.
    @Scheduled(fixedRate = 60_000)
    public void escalateOldAlerts() {
        Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);
        int contactCount = contactRepo.findAllByOrderByPriorityOrderAsc().size();

        for (Alert alert : alertRepo.findByStatusAndLastNotifiedAtBefore("PENDING", cutoff)) {
            int next = alert.getEscalationStage() + 1;
            if (next < contactCount) {
                notifyContact(alert, next);
            }
        }
    }

    // A contact tapped the link in the notification.
    public Optional<Alert> acknowledge(Long id) {
        return alertRepo.findById(id).map(alert -> {
            if ("PENDING".equals(alert.getStatus())) {
                alert.setStatus("ACKNOWLEDGED");
                addToLog(alert, "acknowledged");
                alertRepo.save(alert);
            }
            return alert;
        });
    }

    // The user said it was a false alarm. Tell everyone who already got the alert.
    public Optional<Alert> cancel(Long id) {
        return alertRepo.findById(id).map(alert -> {
            if ("CANCELLED".equals(alert.getStatus())) {
                return alert; // already cancelled, don't send the message twice
            }
            alert.setStatus("CANCELLED");
            addToLog(alert, "cancelled by user");
            alertRepo.save(alert);

            List<Contact> contacts = contactRepo.findAllByOrderByPriorityOrderAsc();
            int notifiedCount = Math.min(alert.getEscalationStage() + 1, contacts.size());
            for (int i = 0; i < notifiedCount; i++) {
                ntfy.send(contacts.get(i).getNtfyTopic(), "Silent Signal: false alarm",
                        "Alert #" + id + " was a false alarm. No action needed.");
            }
            return alert;
        });
    }

    public List<Alert> listAll() {
        return alertRepo.findAllByOrderByCreatedAtDesc();
    }

    // Get the settings row, creating it with default values the first time.
    public AppConfig getConfig() {
        return configRepo.findById(1L).orElseGet(() -> configRepo.save(new AppConfig()));
    }

    private void addToLog(Alert alert, String line) {
        String log = alert.getDeliveryLog();
        alert.setDeliveryLog(log == null || log.isEmpty() ? line : log + "; " + line);
    }
}
