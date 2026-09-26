package com.hackathon.distress.controller;

import com.hackathon.distress.entity.Contact;
import com.hackathon.distress.repository.ContactRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ContactController.java
 * -------------------------
 * Plain CRUD for trusted contacts, used by the /settings page. Nothing
 * clever here on purpose - @Valid on the request body is what gives us
 * "input sanitisation" (rejecting a contact with a blank name or topic)
 * without writing manual if-checks.
 */
@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactRepository contactRepository;

    @GetMapping
    public List<Contact> list() {
        return contactRepository.findAllByOrderByPriorityOrderAsc();
    }

    @PostMapping
    public ResponseEntity<Contact> create(@Valid @RequestBody Contact contact) {
        contact.setId(null); // ignore any client-supplied id; this is always an insert
        Contact saved = contactRepository.save(contact);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Contact> update(@PathVariable Long id, @Valid @RequestBody Contact updated) {
        return contactRepository.findById(id).map(existing -> {
            existing.setName(updated.getName());
            existing.setNtfyTopic(updated.getNtfyTopic());
            existing.setPriorityOrder(updated.getPriorityOrder());
            existing.setPhone(updated.getPhone());
            return ResponseEntity.ok(contactRepository.save(existing));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!contactRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        contactRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
