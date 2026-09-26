package com.hackathon.distress.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// What the Settings page sends when you press Save.
// duressPin: blank = keep the current PIN.
public record ConfigUpdate(
        @Size(max = 1000) String codeWords,
        @Size(max = 100) String cancelCodeWord,
        @Pattern(regexp = "LOW|MEDIUM|HIGH") String sensitivity,
        boolean disguiseEnabled,
        @Pattern(regexp = "calculator|notes") String disguiseType,
        @Pattern(regexp = "^$|^[0-9]{4,8}$", message = "PIN must be 4 to 8 digits") String duressPin,
        boolean analysisActive,
        boolean shareLocation
) {}
