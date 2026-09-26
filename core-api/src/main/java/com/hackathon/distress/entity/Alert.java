package com.hackathon.distress.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Alert.java
 * ----------
 * One row per triggered alert. This is deliberately "explainable": we store
 * the per-signal scores and a human-readable reason string so the dashboard
 * can show *why* an alert fired, not just that it fired.
 *
 * Privacy note (Member 4's threat model): we store only a short transcript
 * *snippet* (last ~10 words around the trigger), never the full audio and
 * never a full transcript. The raw audio itself never reaches this service
 * at all - it lives only in the analysis service's memory for a few seconds.
 */
@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** "codeword", "nonverbal", or "manual_test". */
    @Column(name = "trigger_path", nullable = false)
    private String triggerPath;

    private Integer stressScore;
    private Integer rollingScore;

    /** Human-readable explanation, e.g. "Code word 'red umbrella' matched at 91% confidence". */
    @Column(length = 2000)
    private String reasons;

    /** Last ~10 words of transcript around the trigger moment. Never the full call. */
    @Column(name = "transcript_snippet", length = 500)
    private String transcriptSnippet;

    private Double latitude;
    private Double longitude;

    /** PENDING -> ACKNOWLEDGED or CANCELLED. Starts PENDING until a contact acks or the user cancels. */
    @Column(nullable = false)
    private String status = "PENDING";

    /** How far escalation has gotten: 0 = first contact notified, 1 = second contact notified, etc. */
    private Integer escalationStage = 0;

    private Instant lastNotifiedAt;

    /** Free-text log of ntfy send attempts, e.g. "contact 1: sent; contact 2: FAILED (timeout)". */
    @Column(name = "delivery_log", length = 1000)
    private String deliveryLog = "";
}
