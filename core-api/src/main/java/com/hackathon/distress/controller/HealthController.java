package com.hackathon.distress.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * HealthController.java
 * -----------------------
 * A trivial endpoint so anyone (a teammate, the Step 1 skeleton test, a
 * curl command) can check "is the core API even running?" without needing
 * to know about contacts or alerts yet. This is the very first thing to
 * check when something in the pipeline seems broken.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "core-api");
    }
}
