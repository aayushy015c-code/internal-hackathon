package com.hackathon.distress;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Starts the core API on port 8080.
// @EnableScheduling is needed for the escalation timer in AlertService.
@SpringBootApplication
@EnableScheduling
public class DistressApplication {
    public static void main(String[] args) {
        SpringApplication.run(DistressApplication.class, args);
    }
}
