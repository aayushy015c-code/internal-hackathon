package com.hackathon.distress.controller;

import com.hackathon.distress.entity.AppConfig;
import com.hackathon.distress.repository.AppConfigRepository;
import com.hackathon.distress.service.AlertService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// The user's settings (one row). The analysis service also reads this
// so it knows the code words and sensitivity.
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final AppConfigRepository configRepo;
    private final AlertService alertService;

    public ConfigController(AppConfigRepository configRepo, AlertService alertService) {
        this.configRepo = configRepo;
        this.alertService = alertService;
    }

    @GetMapping
    public AppConfig get() {
        return alertService.getConfig();
    }

    // Settings page "Save" button. The baseline is NOT changed here,
    // only by calibration (see /baseline below).
    @PutMapping
    public AppConfig update(@RequestBody AppConfig updated) {
        AppConfig config = alertService.getConfig();
        config.setCodeWords(updated.getCodeWords());
        config.setCancelCodeWord(updated.getCancelCodeWord());
        if (List.of("LOW", "MEDIUM", "HIGH").contains(updated.getSensitivity())) {
            config.setSensitivity(updated.getSensitivity());
        }
        config.setDisguiseEnabled(updated.isDisguiseEnabled());
        config.setDisguiseType(updated.getDisguiseType());
        config.setDuressPin(updated.getDuressPin());
        config.setAnalysisActive(updated.isAnalysisActive());
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
}
