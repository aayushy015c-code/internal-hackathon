package com.hackathon.distress;

import com.hackathon.distress.entity.Alert;
import com.hackathon.distress.repository.AlertRepository;
import com.hackathon.distress.service.NtfyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CoreApiTests extends UserTestSupport {

    @Autowired AlertRepository alertRepo;
    @Autowired com.hackathon.distress.service.AlertService alertService;
    @MockitoBean NtfyService ntfy; // don't send real notifications in tests

    static final String SETTINGS = """
            {"codeWords":"red umbrella","cancelCodeWord":"false alarm","sensitivity":"MEDIUM",
             "disguiseEnabled":true,"disguiseType":"calculator","duressPin":"%s",
             "analysisActive":true,"shareLocation":%s}""";

    static final String ALERT = """
            {"sessionId":"s1","triggerPath":"codeword","stressScore":10,"reasons":"test",
             "latitude":12.9,"longitude":77.6}""";

    @BeforeEach
    void reset() throws Exception {
        mvc.perform(delete("/api/data")).andExpect(status().isNoContent());
        when(ntfy.send(any(), any(), any(), any())).thenReturn(true);
        mvc.perform(post("/api/contacts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mom\",\"ntfyTopic\":\"silent-signal-test-topic\",\"priorityOrder\":0}"))
                .andExpect(status().isCreated());
    }

    private Alert createAlert() throws Exception {
        mvc.perform(post("/api/alerts").contentType(MediaType.APPLICATION_JSON).content(ALERT))
                .andExpect(status().isCreated());
        return alertRepo.findAllByUserIdOrderByCreatedAtDesc(userId).get(0);
    }

    @Test
    void healthWorks() throws Exception {
        mvc.perform(get("/api/health")).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void duressPinIsHashedAndNeverSentToBrowser() throws Exception {
        mvc.perform(put("/api/config").contentType(MediaType.APPLICATION_JSON).content(SETTINGS.formatted("4821", true)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/config"))
                .andExpect(jsonPath("$.duressPinSet").value(true))
                .andExpect(jsonPath("$.duressPin").doesNotExist())
                .andExpect(jsonPath("$.duressPinHash").doesNotExist())
                .andExpect(content().string(not(containsString("4821"))));
        mvc.perform(get("/api/data/export")).andExpect(content().string(not(containsString("4821"))));

        mvc.perform(post("/api/disguise/check-pin").contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"4821\"}"))
                .andExpect(jsonPath("$.match").value(true));
        mvc.perform(post("/api/disguise/check-pin").contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"0000\"}"))
                .andExpect(jsonPath("$.match").value(false));
    }

    @Test
    void blankPinKeepsOldPin() throws Exception {
        mvc.perform(put("/api/config").contentType(MediaType.APPLICATION_JSON).content(SETTINGS.formatted("4821", true)));
        mvc.perform(put("/api/config").contentType(MediaType.APPLICATION_JSON).content(SETTINGS.formatted("", true)));
        mvc.perform(post("/api/disguise/check-pin").contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"4821\"}"))
                .andExpect(jsonPath("$.match").value(true));
    }

    @Test
    void badInputIsRejected() throws Exception {
        mvc.perform(put("/api/config").contentType(MediaType.APPLICATION_JSON).content(SETTINGS.formatted("12ab", true)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/contacts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"ntfyTopic\":\"../../admin\",\"priorityOrder\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("ntfyTopic")));
        mvc.perform(post("/api/alerts").contentType(MediaType.APPLICATION_JSON).content("{\"sessionId\":\"s\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ackNeedsTheSecretToken() throws Exception {
        Alert alert = createAlert();
        assertEquals(32, alert.getAckToken().length());
        mvc.perform(get("/api/alerts/list")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/alerts/ack/wrong-token")).andExpect(status().isNotFound());
        mvc.perform(get("/api/alerts")).andExpect(content().string(not(containsString(alert.getAckToken()))));

        mvc.perform(get("/api/alerts/ack/" + alert.getAckToken())).andExpect(status().isOk());
        assertEquals("ACKNOWLEDGED", alertRepo.findById(alert.getId()).orElseThrow().getStatus());
    }

    @Test
    void cancelNotifiesOnlyOnce() throws Exception {
        Alert alert = createAlert();
        mvc.perform(post("/api/alerts/" + alert.getId() + "/cancel")).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/alerts/" + alert.getId() + "/cancel")).andExpect(status().isOk());
        verify(ntfy, times(1)).send(any(), eq("Silent Signal: false alarm"), any(), isNull());
    }

    @Test
    void locationIsDroppedWhenSharingIsOff() throws Exception {
        mvc.perform(put("/api/config").contentType(MediaType.APPLICATION_JSON).content(SETTINGS.formatted("", false)));
        assertNull(createAlert().getLatitude());
        mvc.perform(put("/api/config").contentType(MediaType.APPLICATION_JSON).content(SETTINGS.formatted("", true)));
        assertEquals(12.9, createAlert().getLatitude());
    }

    @Test
    void otherHostNamesOnlyReachTheAckLink() throws Exception {
        Alert alert = createAlert();
        mvc.perform(get("/api/config").with(r -> { r.setServerName("attacker.example"); return r; }))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/alerts/ack/" + alert.getAckToken()).with(r -> { r.setServerName("my-tunnel.example"); return r; }))
                .andExpect(status().isOk());
    }

    @Test
    void otherWebsitesAreBlockedByCors() throws Exception {
        mvc.perform(post("/api/alerts/1/cancel").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/health").header("Origin", "http://localhost:5500"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5500"));
    }

    @Test
    void securityHeadersAreSet() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void consentAndDeleteEverything() throws Exception {
        mvc.perform(put("/api/config/consent").contentType(MediaType.APPLICATION_JSON).content("{\"given\":true}"))
                .andExpect(jsonPath("$.consentGiven").value(true));
        createAlert();

        mvc.perform(delete("/api/data")).andExpect(status().isNoContent());
        mvc.perform(get("/api/contacts")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/alerts")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/config")).andExpect(jsonPath("$.consentGiven").value(false));
    }

    @Test
    void oldAlertsAreDeleted() throws Exception {
        Alert alert = createAlert();
        alert.setCreatedAt(Instant.now().minus(40, ChronoUnit.DAYS));
        alertRepo.save(alert);
        alertService.deleteOldAlerts();
        assertTrue(alertRepo.findById(alert.getId()).isEmpty());
    }

}
