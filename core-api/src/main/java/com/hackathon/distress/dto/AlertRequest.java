package com.hackathon.distress.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// The JSON the analysis service sends to POST /api/alerts.
// A Java "record" is just a class that holds data (getters are made for us).
public record AlertRequest(
        @NotBlank String sessionId,
        @NotBlank String triggerPath,
        @NotNull Integer stressScore,
        Integer rollingScore,
        @NotBlank String reasons,
        String transcriptSnippet,
        Double latitude,
        Double longitude
) {}
