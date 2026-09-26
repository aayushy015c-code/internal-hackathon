package com.hackathon.distress.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// The JSON the analysis service sends to POST /api/alerts.
// A Java "record" is just a class that holds data (getters are made for us).
public record AlertRequest(
        @NotBlank String sessionId,
        @NotBlank String triggerPath,
        @NotNull Integer stressScore,
        Integer rollingScore,
        @NotBlank @Size(max = 2000) String reasons,
        @Size(max = 500) String transcriptSnippet,
        Double latitude,
        Double longitude
) {}
