package com.hackathon.distress.entity;

import com.hackathon.distress.config.EncryptedDouble;
import com.hackathon.distress.config.FieldEncryptor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// A trusted person who gets alerts. Lower priorityOrder = notified first.
// ntfyTopic is like a password: anyone who knows it can read the alerts.
// Name and topic are encrypted in the database (FieldEncryptor).
@Entity
@Table(name = "contacts")
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // which user this belongs to (never sent to the browser)
    @JsonIgnore
    @Column(name = "user_id")
    private Long userId;

    @NotBlank
    @Size(max = 100)
    @Convert(converter = FieldEncryptor.class)
    @Column(length = 1000)
    private String name;

    // ntfy only allows letters, numbers, - and _ in topic names
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{8,64}", message = "8-64 letters, numbers, - or _")
    @Convert(converter = FieldEncryptor.class)
    @Column(length = 500)
    private String ntfyTopic;

    @NotNull
    private Integer priorityOrder;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNtfyTopic() { return ntfyTopic; }
    public void setNtfyTopic(String ntfyTopic) { this.ntfyTopic = ntfyTopic; }

    public Integer getPriorityOrder() { return priorityOrder; }
    public void setPriorityOrder(Integer priorityOrder) { this.priorityOrder = priorityOrder; }
}
