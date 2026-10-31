package com.cancerweb.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

/** Conversations between patients or doctors and the administrators, with a notification each way. */
class MessagingTest extends IntegrationTest {

    @Autowired
    private UserRepository users;

    @Test
    void userAndAdminTalkAndEachIsNotified() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        Cookie doctor = signedIn(Role.DOCTOR, "d@example.com");
        String patientId = users.findByEmail("p@example.com").orElseThrow().id();

        mvc.perform(withJson(post("/api/messages"), "{\"body\":\"  \"}").cookie(patient))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.body").exists());
        mvc.perform(withJson(post("/api/messages"), "{\"body\":\"Did you reset my password?\"}").cookie(patient))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/notifications").cookie(admin))
                .andExpect(jsonPath("$.items[0].type").value("USER_MESSAGE"))
                .andExpect(jsonPath("$.items[0].link").value("/admin?tab=inbox"));
        mvc.perform(get("/api/admin/conversations").cookie(admin))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(patientId))
                .andExpect(jsonPath("$[0].unread").value(1))
                .andExpect(jsonPath("$[0].lastMessage").value("Did you reset my password?"));

        mvc.perform(withJson(post("/api/admin/conversations/" + patientId), "{\"body\":\"Yes. Check the status page with your code.\"}")
                .cookie(admin)).andExpect(status().isCreated());
        mvc.perform(post("/api/admin/conversations/" + patientId + "/read").with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/conversations").cookie(admin)).andExpect(jsonPath("$[0].unread").value(0));

        mvc.perform(get("/api/notifications").cookie(patient))
                .andExpect(jsonPath("$.items[0].type").value("ADMIN_MESSAGE"))
                .andExpect(jsonPath("$.items[0].link").value("/messages"));
        mvc.perform(get("/api/messages").cookie(patient))
                .andExpect(jsonPath("$.unread").value(1))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].fromAdmin").value(false))
                .andExpect(jsonPath("$.messages[1].fromAdmin").value(true));
        mvc.perform(post("/api/messages/read").with(csrf()).cookie(patient)).andExpect(status().isNoContent());
        mvc.perform(get("/api/messages").cookie(patient)).andExpect(jsonPath("$.unread").value(0));

        // Each user sees only their own conversation; administrators cannot be messaged as users.
        mvc.perform(get("/api/messages").cookie(doctor)).andExpect(jsonPath("$.messages.length()").value(0));
        mvc.perform(get("/api/messages").cookie(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/conversations").cookie(patient)).andExpect(status().isForbidden());
        String adminId = users.findByEmail("a@example.com").orElseThrow().id();
        mvc.perform(withJson(post("/api/admin/conversations/" + adminId), "{\"body\":\"hi\"}").cookie(admin))
                .andExpect(status().isBadRequest());
    }
}
