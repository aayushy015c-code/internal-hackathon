package com.hackathon.distress.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hackathon.distress.config.EncryptedDouble;
import com.hackathon.distress.config.FieldEncryptor;
import jakarta.persistence.*;

import java.time.Instant;

// One row per alert. We save WHY it fired (reasons) so the dashboard can explain it.
// We only keep the last ~10 words of transcript, never the audio.
// Personal fields are encrypted in the database (FieldEncryptor / EncryptedDouble).
@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // which user this belongs to (never sent to the browser)
    @JsonIgnore
    @Column(name = "user_id")
    private Long userId;

    private Instant createdAt = Instant.now();

    // "codeword", "nonverbal" or "manual_test"
    private String triggerPath;

    private Integer stressScore;
    private Integer rollingScore;

    @Convert(converter = FieldEncryptor.class)
    @Column(length = 4000)
    private String reasons;

    @Convert(converter = FieldEncryptor.class)
    @Column(length = 2000)
    private String transcriptSnippet;

    @Convert(converter = EncryptedDouble.class)
    private Double latitude;
    @Convert(converter = EncryptedDouble.class)
    private Double longitude;

    // PENDING -> ACKNOWLEDGED or CANCELLED
    private String status = "PENDING";

    // 0 = first contact notified, 1 = second contact notified, ...
    private Integer escalationStage = 0;

    private Instant lastNotifiedAt;

    // random secret in the "Acknowledge" link, so nobody can guess it from the alert number
    // Stored encrypted (we need it again when escalating to the next contact),
    // plus a SHA-256 hash of it so we can look it up when the link is tapped.
    @JsonIgnore
    @Convert(converter = FieldEncryptor.class)
    private String ackToken;

    @JsonIgnore
    @Column(unique = true)
    private String ackTokenHash;

    // e.g. "Mom: sent; Dad: FAILED"
    @Convert(converter = FieldEncryptor.class)
    @Column(length = 4000)
    private String deliveryLog = "";

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getTriggerPath() { return triggerPath; }
    public void setTriggerPath(String triggerPath) { this.triggerPath = triggerPath; }

    public Integer getStressScore() { return stressScore; }
    public void setStressScore(Integer stressScore) { this.stressScore = stressScore; }

    public Integer getRollingScore() { return rollingScore; }
    public void setRollingScore(Integer rollingScore) { this.rollingScore = rollingScore; }

    public String getReasons() { return reasons; }
    public void setReasons(String reasons) { this.reasons = reasons; }

    public String getTranscriptSnippet() { return transcriptSnippet; }
    public void setTranscriptSnippet(String transcriptSnippet) { this.transcriptSnippet = transcriptSnippet; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getEscalationStage() { return escalationStage; }
    public void setEscalationStage(Integer escalationStage) { this.escalationStage = escalationStage; }

    public Instant getLastNotifiedAt() { return lastNotifiedAt; }
    public void setLastNotifiedAt(Instant lastNotifiedAt) { this.lastNotifiedAt = lastNotifiedAt; }

    public String getAckToken() { return ackToken; }
    public void setAckToken(String ackToken) { this.ackToken = ackToken; }

    public String getAckTokenHash() { return ackTokenHash; }
    public void setAckTokenHash(String ackTokenHash) { this.ackTokenHash = ackTokenHash; }

    public String getDeliveryLog() { return deliveryLog; }
    public void setDeliveryLog(String deliveryLog) { this.deliveryLog = deliveryLog; }
}
