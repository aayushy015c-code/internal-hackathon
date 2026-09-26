package com.hackathon.distress;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DistressApplication.java
 * -------------------------
 * The entry point of the core API. Running this file's main() method starts
 * an embedded web server on port 8080 (see application.properties) with
 * every @RestController in this project wired up automatically.
 *
 * @EnableScheduling turns on Spring's background job scheduler, which the
 * EscalationService (service/EscalationService.java) uses to periodically
 * check for un-acknowledged alerts and re-notify the next contact.
 */
@SpringBootApplication
@EnableScheduling
public class DistressApplication {
    public static void main(String[] args) {
        SpringApplication.run(DistressApplication.class, args);
    }
}
