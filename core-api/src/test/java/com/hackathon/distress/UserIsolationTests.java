package com.hackathon.distress;

import com.hackathon.distress.entity.Contact;
import com.hackathon.distress.repository.ContactRepository;
import com.hackathon.distress.service.NtfyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class UserIsolationTests extends UserTestSupport {

    @MockitoBean NtfyService ntfy;
    @Autowired ContactRepository contactRepo;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.hackathon.distress.repository.AlertRepository alertRepo;

    static final String SETTINGS = """
            {"codeWords":"%s","cancelCodeWord":"false alarm","sensitivity":"%s","disguiseEnabled":false,
             "disguiseType":"calculator","duressPin":"","analysisActive":true,"shareLocation":true}""";

    private void addContact(MockMvc as, String name, String topic) throws Exception {
        as.perform(post("/api/contacts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"ntfyTopic\":\"" + topic + "\",\"priorityOrder\":0}"))
                .andExpect(status().isCreated());
    }

    @Test
    void newUserGetsAPermanentReadableCallId() throws Exception {
        assertTrue(callId.matches("SS-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}"), callId);
        // asking again (refresh / new tab / restart) gives the same ID
        mvc.perform(get("/api/users/me")).andExpect(jsonPath("$.callId").value(callId));
        mvc.perform(get("/api/users/me")).andExpect(jsonPath("$.callId").value(callId));
        // the key itself is never stored, only its hash
        assertNotEquals(accessKey, userService.require(accessKey).getKeyHash());
    }

    @Test
    void everyUserGetsADifferentId() throws Exception {
        String[] other = register();
        assertNotEquals(callId, other[0]);
        assertNotEquals(accessKey, other[1]);
    }

    @Test
    void noKeyOrWrongKeyIsRejected() throws Exception {
        anonymous.perform(get("/api/config")).andExpect(status().isUnauthorized());
        anonymous.perform(get("/api/contacts")).andExpect(status().isUnauthorized());
        asUser("made-up-key").perform(get("/api/alerts")).andExpect(status().isUnauthorized());
        anonymous.perform(get("/api/health")).andExpect(status().isOk()); // health stays public
    }

    @Test
    void usersNeverSeeEachOthersData() throws Exception {
        MockMvc userB = asUser(register()[1]);

        // A sets things up
        addContact(mvc, "Priya", "silent-signal-topic-of-a");
        mvc.perform(put("/api/config").contentType(MediaType.APPLICATION_JSON).content(SETTINGS.formatted("red umbrella", "HIGH")));
        mvc.perform(post("/api/alerts/test-alert")).andExpect(status().isOk());

        // B sees none of it
        userB.perform(get("/api/contacts")).andExpect(jsonPath("$", hasSize(0)));
        userB.perform(get("/api/alerts")).andExpect(jsonPath("$", hasSize(0)));
        userB.perform(get("/api/config"))
                .andExpect(jsonPath("$.codeWords").value(""))
                .andExpect(jsonPath("$.sensitivity").value("MEDIUM"));
        userB.perform(get("/api/data/export"))
                .andExpect(content().string(not(containsString("Priya"))))
                .andExpect(content().string(not(containsString("red umbrella"))));

        // B can't change or delete A's things even with the right ids
        Contact priya = contactRepo.findAllByUserIdOrderByPriorityOrderAsc(userId).get(0);
        userB.perform(put("/api/contacts/" + priya.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Hacked\",\"ntfyTopic\":\"silent-signal-hacked\",\"priorityOrder\":0}"))
                .andExpect(status().isNotFound());
        userB.perform(delete("/api/contacts/" + priya.getId()));
        Long alertId = alertRepo.findAllByUserIdOrderByCreatedAtDesc(userId).get(0).getId();
        userB.perform(post("/api/alerts/" + alertId + "/cancel")).andExpect(status().isNotFound());
        userB.perform(delete("/api/data")).andExpect(status().isNoContent());

        // A still has everything
        mvc.perform(get("/api/contacts")).andExpect(jsonPath("$[0].name").value("Priya"));
        mvc.perform(get("/api/alerts")).andExpect(jsonPath("$[0].status").value("PENDING"));
        mvc.perform(get("/api/config")).andExpect(jsonPath("$.codeWords").value("red umbrella"));
    }

    @Test
    void userIdsAndKeysAreNotInApiResponses() throws Exception {
        addContact(mvc, "Arjun", "silent-signal-topic-arjun");
        mvc.perform(get("/api/contacts")).andExpect(jsonPath("$[0].userId").doesNotExist());
        mvc.perform(get("/api/users/me")).andExpect(content().string(not(containsString(accessKey))));
    }

    @Test
    void dataFromBeforeUsersExistedGoesToTheFirstUser() throws Exception {
        // an old contact without an owner (saved before this feature)
        Contact old = new Contact();
        old.setName("Old contact");
        old.setNtfyTopic("silent-signal-old-topic");
        old.setPriorityOrder(0);
        contactRepo.save(old);

        // in the app this runs inside register()'s transaction
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(t ->
                ReflectionTestUtils.invokeMethod(userService, "claimDataFromBeforeUsersExisted", userId));
        mvc.perform(get("/api/contacts")).andExpect(jsonPath("$[*].name", hasItem("Old contact")));
    }
}
