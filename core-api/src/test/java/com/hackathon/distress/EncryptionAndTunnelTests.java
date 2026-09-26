package com.hackathon.distress;

import com.hackathon.distress.config.FieldEncryptor;
import com.hackathon.distress.entity.Alert;
import com.hackathon.distress.repository.AlertRepository;
import com.hackathon.distress.service.AlertService;
import com.hackathon.distress.service.NtfyService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EncryptionAndTunnelTests {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AlertRepository alertRepo;
    @Autowired AlertService alertService;
    @MockitoBean NtfyService ntfy;

    @Test
    void personalDataIsEncryptedInTheDatabase() throws Exception {
        mvc.perform(delete("/api/data"));
        mvc.perform(post("/api/contacts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Priya Sharma\",\"ntfyTopic\":\"silent-signal-secret-topic\",\"priorityOrder\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/alerts").contentType(MediaType.APPLICATION_JSON).content("""
                {"sessionId":"s","triggerPath":"codeword","stressScore":1,"reasons":"Code word red umbrella",
                 "transcriptSnippet":"I left my red umbrella","latitude":12.9716,"longitude":77.5946}"""))
                .andExpect(status().isCreated());

        // read the raw rows, like someone who opened the database file would
        String rawRows = jdbc.queryForList("select * from contacts").toString()
                + jdbc.queryForList("select * from alerts").toString();
        for (String secret : List.of("Priya", "silent-signal-secret-topic", "umbrella", "12.9716", "77.5946")) {
            assertFalse(rawRows.contains(secret), "found plain text in database: " + secret);
        }

        // ...but the app still reads it back normally
        mvc.perform(get("/api/contacts")).andExpect(jsonPath("$[0].name").value("Priya Sharma"));
        Alert alert = alertRepo.findAllByOrderByCreatedAtDesc().get(0);
        assertEquals(12.9716, alert.getLatitude());
        assertNotEquals(alert.getAckToken(), alert.getAckTokenHash());
    }

    @Test
    void sameTextEncryptsDifferentlyEachTime() {
        FieldEncryptor enc = new FieldEncryptor("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        String a = enc.convertToDatabaseColumn("red umbrella");
        String b = enc.convertToDatabaseColumn("red umbrella");
        assertNotEquals(a, b); // random IV, so equal values don't look equal
        assertEquals("red umbrella", enc.convertToEntityAttribute(a));
    }

    @Test
    void wrongKeyCannotRead() {
        String stored = new FieldEncryptor("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=").convertToDatabaseColumn("secret");
        FieldEncryptor other = new FieldEncryptor("ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=");
        assertThrows(IllegalStateException.class, () -> other.convertToEntityAttribute(stored));
    }

    @Test
    void missingOrShortKeyIsRejected() {
        assertThrows(IllegalStateException.class, () -> new FieldEncryptor(""));
        assertThrows(IllegalStateException.class, () -> new FieldEncryptor("c2hvcnQ="));
    }

    @Test
    void ackLinkUsesTheTunnelAddressAutomatically() throws Exception {
        // pretend to be cloudflared's /quicktunnel page
        HttpServer fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fake.createContext("/quicktunnel", ex -> {
            byte[] body = "{\"hostname\":\"happy-cat-123.trycloudflare.com\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        fake.start();
        try {
            String url = "http://127.0.0.1:" + fake.getAddress().getPort() + "/quicktunnel";
            ReflectionTestUtils.setField(alertService, "tunnelMetricsUrl", url);
            assertEquals("https://happy-cat-123.trycloudflare.com", ReflectionTestUtils.invokeMethod(alertService, "publicBaseUrl"));

            fake.stop(0); // tunnel gone -> fall back to app.base-url
            assertEquals("http://localhost:8080", ReflectionTestUtils.invokeMethod(alertService, "publicBaseUrl"));
        } finally {
            ReflectionTestUtils.setField(alertService, "tunnelMetricsUrl", "");
        }
    }

    @Test
    void tunnelHostCanOnlyOpenTheAckLink() throws Exception {
        mvc.perform(get("/api/data/export").with(r -> { r.setServerName("happy-cat-123.trycloudflare.com"); return r; }))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/data").with(r -> { r.setServerName("happy-cat-123.trycloudflare.com"); return r; }))
                .andExpect(status().isForbidden());
    }
}
