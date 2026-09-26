package com.hackathon.distress.controller;

import com.hackathon.distress.dto.ConfigUpdate;
import com.hackathon.distress.entity.AppConfig;
import com.hackathon.distress.repository.AppConfigRepository;
import com.hackathon.distress.service.AlertService;
import com.hackathon.distress.service.PinService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

// The user's settings (one row). The analysis service also reads this
// so it knows the code words and sensitivity.
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final AppConfigRepository configRepo;
    private final AlertService alertService;
    private final PinService pinService;

    public ConfigController(AppConfigRepository configRepo, AlertService alertService, PinService pinService) {
        this.configRepo = configRepo;
        this.alertService = alertService;
        this.pinService = pinService;
    }

    @GetMapping
    public AppConfig get() {
        return alertService.getConfig();
    }

    // Settings page "Save" button. The baseline is NOT changed here,
    // only by calibration (see /baseline below).
    @PutMapping
    public AppConfig update(@Valid @RequestBody ConfigUpdate updated) {
        AppConfig config = alertService.getConfig();
        config.setCodeWords(updated.codeWords());
        config.setCancelCodeWord(updated.cancelCodeWord());
        if (updated.sensitivity() != null) config.setSensitivity(updated.sensitivity());
        config.setDisguiseEnabled(updated.disguiseEnabled());
        if (updated.disguiseType() != null) config.setDisguiseType(updated.disguiseType());
        if (updated.duressPin() != null && !updated.duressPin().isEmpty()) {
            config.setDuressPinHash(pinService.hash(updated.duressPin()));
        }
        config.setAnalysisActive(updated.analysisActive());
        config.setShareLocation(updated.shareLocation());
        audit.info("settings updated");
        return configRepo.save(config);
    }

    // Called by the analysis service after the user records their normal voice.
    @PutMapping("/baseline")
    public AppConfig updateBaseline(@RequestBody AppConfig baseline) {
        AppConfig config = alertService.getConfig();
        config.setBaselinePitchHz(baseline.getBaselinePitchHz());
        config.setBaselineRms(baseline.getBaselineRms());
        return configRepo.save(config);
    }

    // consent.html: the user agrees to (or withdraws from) the privacy notice
    @PutMapping("/consent")
    public AppConfig consent(@RequestBody ConsentRequest request) {
        AppConfig config = alertService.getConfig();
        config.setConsentGiven(request.given());
        config.setConsentAt(request.given() ? Instant.now() : null);
        audit.info("consent {}", request.given() ? "given" : "withdrawn");
        return configRepo.save(config);
    }

    public record ConsentRequest(boolean given) {}
}
