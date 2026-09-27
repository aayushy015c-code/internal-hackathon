package com.hackathon.distress.controller;

import com.hackathon.distress.entity.Contact;
import com.hackathon.distress.repository.ContactRepository;
import com.hackathon.distress.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Add / edit / delete trusted contacts (Settings page). Only ever the current user's contacts.
@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactRepository contactRepo;
    private final UserService users;

    public ContactController(ContactRepository contactRepo, UserService users) {
        this.contactRepo = contactRepo;
        this.users = users;
    }

    @GetMapping
    public List<Contact> list(@RequestHeader(value = UserService.HEADER, required = false) String key) {
        return contactRepo.findAllByUserIdOrderByPriorityOrderAsc(users.require(key).getId());
    }

    @PostMapping
    public ResponseEntity<Contact> create(@RequestHeader(value = UserService.HEADER, required = false) String key, @Valid @RequestBody Contact contact) {
        contact.setId(null); // always a new contact
        contact.setUserId(users.require(key).getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(contactRepo.save(contact));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Contact> update(@RequestHeader(value = UserService.HEADER, required = false) String key, @PathVariable Long id, @Valid @RequestBody Contact updated) {
        return contactRepo.findByIdAndUserId(id, users.require(key).getId()).map(contact -> {
            contact.setName(updated.getName());
            contact.setNtfyTopic(updated.getNtfyTopic());
            contact.setPriorityOrder(updated.getPriorityOrder());
            return ResponseEntity.ok(contactRepo.save(contact));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(value = UserService.HEADER, required = false) String key, @PathVariable Long id) {
        contactRepo.findByIdAndUserId(id, users.require(key).getId()).ifPresent(contactRepo::delete);
        return ResponseEntity.noContent().build();
    }
}
