package com.hackathon.distress.entity;

import jakarta.persistence.*;

// The user's settings. There is only one user, so there is only one row (id = 1).
@Entity
@Table(name = "app_config")
public class AppConfig {

    @Id
    private Long id = 1L;

    // comma separated, e.g. "red umbrella,call my sister"
    @Column(length = 1000)
    private String codeWords = "";

    // saying this during a call cancels the last alert
    private String cancelCodeWord = "false alarm";

    // LOW, MEDIUM or HIGH
    private String sensitivity = "MEDIUM";

    // the user's normal voice, set by calibration (null = not calibrated yet)
    private Double baselinePitchHz;
    private Double baselineRms;

    private boolean disguiseEnabled = false;
    private String disguiseType = "calculator"; // "calculator" or "notes"
    private String duressPin = "0000";

    // master on/off switch for voice analysis
    private boolean analysisActive = true;

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

    public String getDuressPin() { return duressPin; }
    public void setDuressPin(String duressPin) { this.duressPin = duressPin; }

    public boolean isAnalysisActive() { return analysisActive; }
    public void setAnalysisActive(boolean analysisActive) { this.analysisActive = analysisActive; }
}
