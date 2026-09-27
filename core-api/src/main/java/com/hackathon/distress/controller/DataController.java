package com.hackathon.distress.controller;

import com.hackathon.distress.entity.AppConfig;
import com.hackathon.distress.repository.AlertRepository;
import com.hackathon.distress.repository.AppConfigRepository;
import com.hackathon.distress.repository.ContactRepository;
import com.hackathon.distress.entity.AppUser;
import com.hackathon.distress.service.AlertService;
import com.hackathon.distress.service.UserService;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.springframework.beans.BeanUtils;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// Your data, your rights (DPDP Act): download everything we store about you, or delete it.
// Only the current user's data; other users on the same computer are not affected.
@RestController
@RequestMapping("/api/data")
public class DataController {

    private static final Logger audit = LoggerFactory.getLogger("audit");

    private final AlertRepository alertRepo;
    private final ContactRepository contactRepo;
    private final AppConfigRepository configRepo;
    private final AlertService alertService;
    private final UserService users;

    public DataController(AlertRepository alertRepo, ContactRepository contactRepo,
                          AppConfigRepository configRepo, AlertService alertService, UserService users) {
        this.alertRepo = alertRepo;
        this.contactRepo = contactRepo;
        this.configRepo = configRepo;
        this.alertService = alertService;
        this.users = users;
    }

    @GetMapping("/export")
    public Map<String, Object> export(@RequestHeader(value = UserService.HEADER, required = false) String key) {
        AppUser me = users.require(key);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportedAt", Instant.now());
        data.put("callId", me.getCallId());
        data.put("settings", alertService.getConfig(me.getId()));
        data.put("contacts", contactRepo.findAllByUserIdOrderByPriorityOrderAsc(me.getId()));
        data.put("alerts", alertRepo.findAllByUserIdOrderByCreatedAtDesc(me.getId()));
        audit.info("data exported");
        return data;
    }

    // Deletes your contacts, alerts and settings. Your call ID stays, so people can still call you.
    @DeleteMapping
    @Transactional
    public ResponseEntity<Void> deleteEverything(@RequestHeader(value = UserService.HEADER, required = false) String key) {
        Long me = users.require(key).getId();
        alertRepo.deleteAllForUser(me);
        contactRepo.deleteAllForUser(me);
        // back to default settings (and no consent), keeping the same row
        AppConfig config = alertService.getConfig(me);
        BeanUtils.copyProperties(new AppConfig(me), config);
        configRepo.save(config);
        audit.info("all user data deleted");
        return ResponseEntity.noContent().build();
    }
}
