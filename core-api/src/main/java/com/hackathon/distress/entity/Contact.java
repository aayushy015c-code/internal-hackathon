package com.hackathon.distress.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contact.java
 * ------------
 * One trusted emergency contact. `priorityOrder` decides who gets notified
 * first (lower number = notified first); the EscalationService walks this
 * list in order when nobody acknowledges an alert.
 *
 * `ntfyTopic` is the "address" we push notifications to via ntfy.sh. Treat
 * it like a password - anyone who knows the topic name can read that
 * contact's alerts, so the frontend should generate a long random string,
 * never something guessable like "sakshi-emergency".
 */
@Entity
@Table(name = "contacts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Column(name = "ntfy_topic", nullable = false)
    private String ntfyTopic;

    @NotNull
    @Column(name = "priority_order", nullable = false)
    private Integer priorityOrder;

    /** Optional phone number, shown in the notification text only - we never call it automatically. */
    private String phone;
}
