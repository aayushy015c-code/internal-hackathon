package com.hackathon.distress;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.distress.config.LocalOnlyFilter;
import com.hackathon.distress.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Every test class that extends this gets a brand new user, and `mvc` sends that
// user's access key with every request (like the browser does).
abstract class UserTestSupport {

    @Autowired WebApplicationContext context;
    @Autowired LocalOnlyFilter localOnlyFilter;
    @Autowired UserService userService;
    @Autowired ObjectMapper json;

    MockMvc anonymous; // no access key
    MockMvc mvc;       // as the test user
    String accessKey;
    String callId;
    Long userId;

    @BeforeEach
    void newUser() throws Exception {
        anonymous = MockMvcBuilders.webAppContextSetup(context).addFilters(localOnlyFilter).build();
        String[] user = register();
        callId = user[0];
        accessKey = user[1];
        userId = userService.require(accessKey).getId();
        mvc = asUser(accessKey);
    }

    String[] register() throws Exception {
        String body = anonymous.perform(post("/api/users")).andReturn().getResponse().getContentAsString();
        Map<?, ?> m = json.readValue(body, Map.class);
        return new String[] {(String) m.get("callId"), (String) m.get("accessKey")};
    }

    MockMvc asUser(String key) {
        return MockMvcBuilders.webAppContextSetup(context).addFilters(localOnlyFilter)
                .defaultRequest(get("/").header(UserService.HEADER, key)).build();
    }
}
