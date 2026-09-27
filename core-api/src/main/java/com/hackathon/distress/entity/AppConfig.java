package com.hackathon.distress.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hackathon.distress.config.EncryptedDouble;
import com.hackathon.distress.config.FieldEncryptor;
import jakarta.persistence.*;

import java.time.Instant;

// One user's settings. One row per user; the row id is the user's id.
@Entity
@Table(name = "app_config")
public class AppConfig {

    @Id
    private Long id;

    public AppConfig() {
    }

    public AppConfig(Long userId) {
        this.id = userId;
    }

    // comma separated, e.g. "red umbrella,call my sister"
    @Convert(converter = FieldEncryptor.class)
    @Column(length = 4000)
    private String codeWords = "";

    // saying this during a call cancels the last alert
    @Convert(converter = FieldEncryptor.class)
    @Column(length = 1000)
    private String cancelCodeWord = "false alarm";

    // LOW, MEDIUM or HIGH
    private String sensitivity = "MEDIUM";

    // the user's normal voice, set by calibration (null = not calibrated yet)
    // (a voice pattern counts as sensitive/biometric data, so it's encrypted)
    @Convert(converter = EncryptedDouble.class)
    private Double baselinePitchHz;
    @Convert(converter = EncryptedDouble.class)
    private Double baselineRms;

    private boolean disguiseEnabled = false;
    private String disguiseType = "calculator"; // "calculator" or "notes"

    // salted hash of the duress PIN, never the PIN itself, and never sent to the browser
    @JsonIgnore
    private String duressPinHash;

    // master on/off switch for voice analysis
    private boolean analysisActive = true;

    // the user agreed to the privacy notice (consent.html). Nothing is analyzed without it.
    private boolean consentGiven = false;
    private Instant consentAt;

    // include GPS location in alerts
    private boolean shareLocation = true;

    // so the settings page can show "PIN is set" without knowing the PIN
    public boolean isDuressPinSet() { return duressPinHash != null; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodeWords() { return codeWords; }
    public void setCodeWords(String codeWords) { this.codeWords = codeWords; }

    public String getCancelCodeWord() { return cancelCodeWord; }
    public void setCancelCodeWord(String cancelCodeWord) { this.cancelCodeWord = cancelCodeWord; }

    public String getSensitivity() { return sensitivity; }
    public void setSensitivity(String sensitivity) { this.sensitivity = sensitivity; }

    public Double getBaselinePitchHz() { return baselinePitchHz; }
    public void setBaselinePitchHz(Double baselinePitchHz) { this.baselinePitchHz = baselinePitchHz; }

    public Double getBaselineRms() { return baselineRms; }
    public void setBaselineRms(Double baselineRms) { this.baselineRms = baselineRms; }

    public boolean isDisguiseEnabled() { return disguiseEnabled; }
    public void setDisguiseEnabled(boolean disguiseEnabled) { this.disguiseEnabled = disguiseEnabled; }

    public String getDisguiseType() { return disguiseType; }
    public void setDisguiseType(String disguiseType) { this.disguiseType = disguiseType; }

    public String getDuressPinHash() { return duressPinHash; }
    public void setDuressPinHash(String duressPinHash) { this.duressPinHash = duressPinHash; }

    public boolean isAnalysisActive() { return analysisActive; }
    public void setAnalysisActive(boolean analysisActive) { this.analysisActive = analysisActive; }

    public boolean isConsentGiven() { return consentGiven; }
    public void setConsentGiven(boolean consentGiven) { this.consentGiven = consentGiven; }

    public Instant getConsentAt() { return consentAt; }
    public void setConsentAt(Instant consentAt) { this.consentAt = consentAt; }

    public boolean isShareLocation() { return shareLocation; }
    public void setShareLocation(boolean shareLocation) { this.shareLocation = shareLocation; }
}
