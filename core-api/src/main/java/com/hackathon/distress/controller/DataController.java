package com.hackathon.distress.controller;

import com.hackathon.distress.entity.AppConfig;
import com.hackathon.distress.repository.AlertRepository;
import com.hackathon.distress.repository.AppConfigRepository;
import com.hackathon.distress.repository.ContactRepository;
import com.hackathon.distress.service.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// Your data, your rights (DPDP Act): download everything we store, or delete all of it.
@RestController
@RequestMapping("/api/data")
public class DataController {

    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final AlertRepository alertRepo;
    private final ContactRepository contactRepo;
    private final AppConfigRepository configRepo;
    private final AlertService alertService;

    public DataController(AlertRepository alertRepo, ContactRepository contactRepo,
                          AppConfigRepository configRepo, AlertService alertService) {
        this.alertRepo = alertRepo;
        this.contactRepo = contactRepo;
        this.configRepo = configRepo;
        this.alertService = alertService;
    }

    @GetMapping("/export")
    public Map<String, Object> export() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportedAt", Instant.now());
        data.put("settings", alertService.getConfig());
        data.put("contacts", contactRepo.findAll());
        data.put("alerts", alertRepo.findAll());
        audit.info("data exported");
        return data;
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteEverything() {
        alertRepo.deleteAll();
        contactRepo.deleteAll();
        configRepo.deleteAll();
        configRepo.save(new AppConfig()); // back to default settings (and no consent)
        audit.info("all user data deleted");
        return ResponseEntity.noContent().build();
    }
}
