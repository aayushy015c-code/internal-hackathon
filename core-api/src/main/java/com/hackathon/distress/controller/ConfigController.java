package com.hackathon.distress.controller;

import com.hackathon.distress.entity.AppConfig;
import com.hackathon.distress.repository.AppConfigRepository;
import com.hackathon.distress.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * ConfigController.java
 * -----------------------
 * The single AppConfig row: code words, sensitivity, baseline, disguise
 * settings, duress PIN, and the "analysis active" consent flag.
 *
 * This is the row the analysis service (FastAPI) reads on startup and
 * after calibration/settings changes - see analysis-service/config.py's
 * `refresh_from_core_api()`. Spring Boot is always the source of truth;
 * FastAPI just keeps a fast in-memory copy.
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final AppConfigRepository appConfigRepository;
    private final AlertService alertService;

    @GetMapping
    public AppConfig get() {
        return alertService.getConfig();
    }

    @PutMapping
    public AppConfig update(@Valid @RequestBody AppConfig updated) {
        AppConfig existing = alertService.getConfig();
        existing.setCodeWords(updated.getCodeWords());
        existing.setCancelCodeWord(updated.getCancelCodeWord());
        existing.setSensitivity(updated.getSensitivity());
        existing.setDisguiseEnabled(updated.isDisguiseEnabled());
        existing.setDisguiseType(updated.getDisguiseType());
        existing.setDuressPin(updated.getDuressPin());
        existing.setAnalysisActive(updated.isAnalysisActive());
        // Baseline is set separately by /calibrate on the analysis service (see below),
        // but we still accept it here in case the frontend wants to clear/reset it.
        if (updated.getBaselinePitchHz() != null) existing.setBaselinePitchHz(updated.getBaselinePitchHz());
        if (updated.getBaselineRms() != null) existing.setBaselineRms(updated.getBaselineRms());
        return appConfigRepository.save(existing);
    }

    /**
     * Called by the analysis service right after a successful /calibrate,
     * so the baseline is persisted centrally instead of only living in
     * FastAPI's memory (which would be lost on restart).
     */
    @PutMapping("/baseline")
    public AppConfig updateBaseline(@RequestBody BaselineUpdate baseline) {
        AppConfig existing = alertService.getConfig();
        existing.setBaselinePitchHz(baseline.baselinePitchHz());
        existing.setBaselineRms(baseline.baselineRms());
        return appConfigRepository.save(existing);
    }

    public record BaselineUpdate(Double baselinePitchHz, Double baselineRms) {}
}
