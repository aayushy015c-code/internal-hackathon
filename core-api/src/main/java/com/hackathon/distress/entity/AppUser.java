package com.hackathon.distress.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.Instant;

// One person using the app.
//   callId    = public, permanent. Others type it to call you, e.g. "SS-K7P3-9QDM-X2WA".
//   keyHash   = SHA-256 of your secret access key. The browser keeps the key and
//               sends it with every request; it works like a password, so we only store its hash.
// Every contact, alert and settings row belongs to one user (userId).
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String callId;

    @JsonIgnore
    @Column(nullable = false, unique = true, length = 64)
    private String keyHash;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
