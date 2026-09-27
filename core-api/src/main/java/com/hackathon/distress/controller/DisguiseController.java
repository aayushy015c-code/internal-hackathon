package com.hackathon.distress.controller;

import com.hackathon.distress.entity.AppConfig;
import com.hackathon.distress.service.AlertService;
import com.hackathon.distress.service.PinService;
import com.hackathon.distress.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Used by the fake calculator/notes page. It only gets what it needs:
// which fake app to show, and "yes/no" for a PIN. The PIN itself never leaves the server.
@RestController
@RequestMapping("/api/disguise")
public class DisguiseController {

    private final AlertService alertService;
    private final PinService pinService;
    private final UserService users;

    public DisguiseController(AlertService alertService, PinService pinService, UserService users) {
        this.alertService = alertService;
        this.pinService = pinService;
        this.users = users;
    }

    @GetMapping
    public Map<String, Object> info(@RequestHeader(value = UserService.HEADER, required = false) String key) {
        AppConfig config = alertService.getConfig(users.require(key).getId());
        return Map.of("enabled", config.isDisguiseEnabled(), "type", config.getDisguiseType());
    }

    @PostMapping("/check-pin")
    public Map<String, Boolean> checkPin(@RequestHeader(value = UserService.HEADER, required = false) String key, @RequestBody Map<String, String> body) {
        String stored = alertService.getConfig(users.require(key).getId()).getDuressPinHash();
        return Map.of("match", pinService.matches(body.get("pin"), stored));
    }
}
