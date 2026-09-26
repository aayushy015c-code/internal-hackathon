package com.hackathon.distress.controller;

import com.hackathon.distress.entity.AppConfig;
import com.hackathon.distress.service.AlertService;
import com.hackathon.distress.service.PinService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Used by the fake calculator/notes page. It only gets what it needs:
// which fake app to show, and "yes/no" for a PIN. The PIN itself never leaves the server.
@RestController
@RequestMapping("/api/disguise")
public class DisguiseController {

    private final AlertService alertService;
    private final PinService pinService;

    public DisguiseController(AlertService alertService, PinService pinService) {
        this.alertService = alertService;
        this.pinService = pinService;
    }

    @GetMapping
    public Map<String, Object> info() {
        AppConfig config = alertService.getConfig();
        return Map.of("enabled", config.isDisguiseEnabled(), "type", config.getDisguiseType());
    }

    @PostMapping("/check-pin")
    public Map<String, Boolean> checkPin(@RequestBody Map<String, String> body) {
        String stored = alertService.getConfig().getDuressPinHash();
        return Map.of("match", pinService.matches(body.get("pin"), stored));
    }
}
