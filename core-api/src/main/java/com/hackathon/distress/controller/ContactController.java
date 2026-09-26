package com.hackathon.distress.controller;

import com.hackathon.distress.entity.Contact;
import com.hackathon.distress.repository.ContactRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Add / edit / delete trusted contacts (Settings page).
@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactRepository contactRepo;

    public ContactController(ContactRepository contactRepo) {
        this.contactRepo = contactRepo;
    }

    @GetMapping
    public List<Contact> list() {
        return contactRepo.findAllByOrderByPriorityOrderAsc();
    }

    @PostMapping
    public ResponseEntity<Contact> create(@Valid @RequestBody Contact contact) {
        contact.setId(null); // always a new contact
        return ResponseEntity.status(HttpStatus.CREATED).body(contactRepo.save(contact));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Contact> update(@PathVariable Long id, @Valid @RequestBody Contact updated) {
        return contactRepo.findById(id).map(contact -> {
            contact.setName(updated.getName());
            contact.setNtfyTopic(updated.getNtfyTopic());
            contact.setPriorityOrder(updated.getPriorityOrder());
            return ResponseEntity.ok(contactRepo.save(contact));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        contactRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
