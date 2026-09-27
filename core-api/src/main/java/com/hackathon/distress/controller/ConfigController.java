package com.hackathon.distress.controller;

import com.hackathon.distress.dto.ConfigUpdate;
import com.hackathon.distress.entity.AppConfig;
import com.hackathon.distress.repository.AppConfigRepository;
import com.hackathon.distress.service.AlertService;
import com.hackathon.distress.service.PinService;
import com.hackathon.distress.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

// The current user's settings (one row per user). The analysis service also reads this
// so it knows the code words and sensitivity.
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final AppConfigRepository configRepo;
    private final AlertService alertService;
    private final PinService pinService;
    private final UserService users;

    public ConfigController(AppConfigRepository configRepo, AlertService alertService, PinService pinService, UserService users) {
        this.configRepo = configRepo;
        this.alertService = alertService;
        this.pinService = pinService;
        this.users = users;
    }

    private AppConfig mine(String key) {
        return alertService.getConfig(users.require(key).getId());
    }

    @GetMapping
    public AppConfig get(@RequestHeader(value = UserService.HEADER, required = false) String key) {
        return mine(key);
    }

    // Settings page "Save" button. The baseline is NOT changed here,
    // only by calibration (see /baseline below).
    @PutMapping
    public AppConfig update(@RequestHeader(value = UserService.HEADER, required = false) String key, @Valid @RequestBody ConfigUpdate updated) {
        AppConfig config = mine(key);
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
    public AppConfig updateBaseline(@RequestHeader(value = UserService.HEADER, required = false) String key, @RequestBody AppConfig baseline) {
        AppConfig config = mine(key);
        config.setBaselinePitchHz(baseline.getBaselinePitchHz());
        config.setBaselineRms(baseline.getBaselineRms());
        return configRepo.save(config);
    }

    // consent.html: the user agrees to (or withdraws from) the privacy notice
    @PutMapping("/consent")
    public AppConfig consent(@RequestHeader(value = UserService.HEADER, required = false) String key, @RequestBody ConsentRequest request) {
        AppConfig config = mine(key);
        config.setConsentGiven(request.given());
        config.setConsentAt(request.given() ? Instant.now() : null);
        audit.info("consent {}", request.given() ? "given" : "withdrawn");
        return configRepo.save(config);
    }

    public record ConsentRequest(boolean given) {}
}
