package com.hackathon.distress.service;

import com.hackathon.distress.dto.AlertRequest;
import com.hackathon.distress.entity.AppConfig;
import com.hackathon.distress.entity.Alert;
import com.hackathon.distress.entity.Contact;
import com.hackathon.distress.repository.AlertRepository;
import com.hackathon.distress.repository.AppConfigRepository;
import com.hackathon.distress.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * AlertService.java
 * ------------------
 * The business logic for alerts: create one, notify the first contact,
 * escalate to the next contact if nobody acknowledges in time, handle an
 * acknowledgement, and handle a "false alarm" cancel.
 *
 * This is the one place that decides "who gets notified right now" - the
 * controller (AlertController) just translates HTTP requests into calls
 * on this class, and EscalationService (a scheduled job) calls back into
 * this class's escalate() method every minute.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final ContactRepository contactRepository;
    private final AppConfigRepository appConfigRepository;
    private final NtfyService ntfyService;

    @Value("${app.base-url}")
    private String baseUrl;

    /** Creates and persists a new alert, then sends it to the highest-priority contact. */
    public Alert createAndDispatch(AlertRequest req) {
        Alert alert = new Alert();
        alert.setCreatedAt(Instant.now());
        alert.setTriggerPath(req.getTriggerPath());
        alert.setStressScore(req.getStressScore());
        alert.setRollingScore(req.getRollingScore());
        alert.setReasons(req.getReasons());
        alert.setTranscriptSnippet(req.getTranscriptSnippet());
        alert.setLatitude(req.getLatitude());
        alert.setLongitude(req.getLongitude());
        alert.setStatus("PENDING");
        alert.setEscalationStage(0);

        alert = alertRepository.save(alert);
        notifyContactAtStage(alert, 0);
        return alert;
    }

    /** Sends a real alert to every configured contact immediately, for the "test alert" button in Settings. */
    public Alert createTestAlert() {
        AlertRequest req = new AlertRequest();
        req.setSessionId("manual-test");
        req.setTriggerPath("manual_test");
        req.setStressScore(0);
        req.setRollingScore(0);
        req.setReasons("Manual test alert triggered from Settings.");
        req.setTranscriptSnippet("");
        return createAndDispatch(req);
    }

    /** Sends the notification to the Nth contact in priority order (0 = first/highest priority). */
    private void notifyContactAtStage(Alert alert, int stage) {
        List<Contact> contacts = contactRepository.findAllByOrderByPriorityOrderAsc();
        if (contacts.isEmpty()) {
            appendDeliveryLog(alert, "no contacts configured - nothing sent");
            alertRepository.save(alert);
            return;
        }
        if (stage >= contacts.size()) {
            appendDeliveryLog(alert, "escalation exhausted all " + contacts.size() + " contacts");
            alertRepository.save(alert);
            return;
        }

        Contact contact = contacts.get(stage);
        String ackUrl = baseUrl + "/api/alerts/" + alert.getId() + "/ack";
        boolean sent = ntfyService.sendAlert(contact, alert, ackUrl);

        appendDeliveryLog(alert, "contact '" + contact.getName() + "' (stage " + stage + "): "
                + (sent ? "sent" : "FAILED"));
        alert.setEscalationStage(stage);
        alert.setLastNotifiedAt(Instant.now());
        alertRepository.save(alert);
    }

    private void appendDeliveryLog(Alert alert, String line) {
        String existing = alert.getDeliveryLog() == null ? "" : alert.getDeliveryLog();
        String sep = existing.isEmpty() ? "" : "; ";
        alert.setDeliveryLog(existing + sep + line);
    }

    /**
     * Called every minute by EscalationService. For every PENDING alert whose
     * last notification is older than the timeout, notify the next contact.
     */
    public void escalateOverdueAlerts(Instant cutoff) {
        List<Alert> overdue = alertRepository.findByStatusAndLastNotifiedAtBefore("PENDING", cutoff);
        for (Alert alert : overdue) {
            int nextStage = alert.getEscalationStage() + 1;
            List<Contact> contacts = contactRepository.findAllByOrderByPriorityOrderAsc();
            if (nextStage < contacts.size()) {
                log.info("Escalating alert {} to stage {}", alert.getId(), nextStage);
                notifyContactAtStage(alert, nextStage);
            }
        }
    }

    /** A contact clicked "Acknowledge" in their notification. */
    public Optional<Alert> acknowledge(Long alertId) {
        return alertRepository.findById(alertId).map(alert -> {
            alert.setStatus("ACKNOWLEDGED");
            appendDeliveryLog(alert, "acknowledged at " + Instant.now());
            return alertRepository.save(alert);
        });
    }

    /** The user says this was a false alarm - mark it cancelled and let contacts know via a follow-up ntfy push. */
    public Optional<Alert> cancel(Long alertId) {
        return alertRepository.findById(alertId).map(alert -> {
            alert.setStatus("CANCELLED");
            appendDeliveryLog(alert, "cancelled by user at " + Instant.now());
            Alert saved = alertRepository.save(alert);

            // Best-effort courtesy notification; failures here must not block the cancel itself.
            List<Contact> contacts = contactRepository.findAllByOrderByPriorityOrderAsc();
            Alert cancelNotice = new Alert();
            cancelNotice.setTriggerPath("cancel_notice");
            cancelNotice.setReasons("Previous alert #" + alertId + " was marked a FALSE ALARM by the user.");
            cancelNotice.setCreatedAt(Instant.now());
            for (Contact c : contacts) {
                ntfyService.sendAlert(c, cancelNotice, baseUrl + "/dashboard");
            }
            return saved;
        });
    }

    /** Finds the most recent PENDING alert - used by the spoken "cancel code word" path. */
    public Optional<Alert> mostRecentPending() {
        return alertRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> "PENDING".equals(a.getStatus()))
                .findFirst();
    }

    public List<Alert> listAll() {
        return alertRepository.findAllByOrderByCreatedAtDesc();
    }

    public AppConfig getConfig() {
        return appConfigRepository.findById(1L).orElseGet(() -> {
            AppConfig cfg = new AppConfig();
            cfg.setId(1L);
            return appConfigRepository.save(cfg);
        });
    }
}
