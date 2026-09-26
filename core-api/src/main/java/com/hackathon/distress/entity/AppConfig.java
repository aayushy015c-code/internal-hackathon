package com.hackathon.distress.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AppConfig.java
 * --------------
 * Named "AppConfig" (not "Config") to avoid clashing with Spring's own
 * @Configuration classes. This is a *singleton* row - the whole app has one
 * user, so there is exactly one config record, always with id = 1.
 *
 * codeWords / cancelCodeWord are stored comma-separated for simplicity
 * (this is a hackathon MVP, not a multi-tenant product). The analysis
 * service asks the core API for this row so it knows what to listen for.
 */
@Entity
@Table(name = "app_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppConfig {

    @Id
    private Long id = 1L;

    /** Comma-separated phrases, e.g. "red umbrella,call my sister". */
    @Column(name = "code_words", length = 1000)
    private String codeWords = "";

    /** Spoken phrase (or empty) that marks the most recent alert as a false alarm. */
    @Column(name = "cancel_code_word")
    private String cancelCodeWord = "false alarm";

    /** LOW, MEDIUM, or HIGH. Maps to stress-score thresholds 80 / 65 / 50 in the analysis service. */
    @Column(nullable = false)
    private String sensitivity = "MEDIUM";

    /** Baseline pitch (Hz) and RMS energy from the last successful /calibrate call. Null until calibrated. */
    private Double baselinePitchHz;
    private Double baselineRms;

    /** Whether disguise mode is turned on, and which fake screen to show. */
    private boolean disguiseEnabled = false;

    /** "calculator" or "notes". */
    private String disguiseType = "calculator";

    /** PIN that opens a fake, empty dashboard instead of the real one under duress. */
    private String duressPin = "0000";

    /** Whether the analysis loop is currently allowed to run (the "one-tap stop" for consent). */
    private boolean analysisActive = true;
}
