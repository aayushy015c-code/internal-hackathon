package com.hackathon.distress.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * AlertRequest.java
 * ------------------
 * The JSON body the analysis service (FastAPI) sends to POST /api/alerts
 * when it decides an alert should fire. This is a DTO (data transfer
 * object), not the Alert entity itself - it's the shape of the *request*,
 * which is slightly different from what we store (we add id, createdAt,
 * status, etc. ourselves).
 *
 * The analysis service is the one place that knows the full "why" of a
 * trigger, so it also builds the human-readable `reasons` string - the
 * core API just stores and forwards it.
 */
@Getter
@Setter
public class AlertRequest {

    @NotBlank
    private String sessionId;

    /** "codeword", "nonverbal", or "manual_test". */
    @NotBlank
    private String triggerPath;

    @NotNull
    private Integer stressScore;

    private Integer rollingScore;

    @NotBlank
    private String reasons;

    /** Last ~10 words of transcript around the trigger. May be blank if transcription failed. */
    private String transcriptSnippet;

    /** Browser geolocation, forwarded through from the /analyze request. Null if the user denied location. */
    private Double latitude;
    private Double longitude;
}
