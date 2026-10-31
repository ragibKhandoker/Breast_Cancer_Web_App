package com.cancerweb.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.cancerweb.backend.model.Notification;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.repository.NotificationRepository;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** The bell: each user's own notifications, newest first, made by the things that happen on the site. */
class NotificationApiTest extends IntegrationTest {

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private UserRepository users;

    @Autowired
    private com.cancerweb.backend.service.PasswordHelpService passwordHelp;

    private String id(String email) {
        return users.findByEmail(email).orElseThrow().id();
    }

    @Test
    void listsOnlyTheUsersOwnNotificationsNewestFirstAndMarksThemRead() throws Exception {
        Cookie me = signedIn(Role.PATIENT, "p@example.com");
        Cookie other = signedIn(Role.PATIENT, "q@example.com");
        Instant now = Instant.now();
        notifications.saveAll(List.of(
                new Notification(null, id("p@example.com"), "X", "Older", "a", "/profile", false, now.minusSeconds(60), null),
                new Notification(null, id("p@example.com"), "X", "Newest", "b", "/profile", false, now, null),
                new Notification(null, id("q@example.com"), "X", "Not yours", "c", null, false, now, null)));

        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        String body = mvc.perform(get("/api/notifications").cookie(me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].title").value("Newest"))
                .andExpect(jsonPath("$.items[1].title").value("Older"))
                .andReturn().getResponse().getContentAsString();
        String newest = JsonPath.read(body, "$.items[0].id");
        String theirs = notifications.findAll().stream().filter(n -> n.title().equals("Not yours")).findFirst().orElseThrow().id();

        mvc.perform(patch("/api/notifications/" + theirs + "/read").with(csrf()).cookie(me)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/notifications/" + newest + "/read").with(csrf()).cookie(me))
                .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));
        mvc.perform(get("/api/notifications/unread-count").cookie(me)).andExpect(jsonPath("$.unread").value(1));
        mvc.perform(post("/api/notifications/read-all").with(csrf()).cookie(me)).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").cookie(me)).andExpect(jsonPath("$.unread").value(0));
        mvc.perform(get("/api/notifications/unread-count").cookie(other)).andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    void passwordHelpReachesTheAdminEvenForAnOpenRequestOrOneMadeBeforeNotifications() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        createUser("Bilkis Akter", "bilkis@gmail.com", Role.PATIENT);
        String help = "{\"email\":\"bilkis@gmail.com\",\"message\":\"I have forgot my password, please help\"}";

        // First request: the admin is told, with the user's message.
        mvc.perform(withJson(post("/api/auth/password-help"), help)).andExpect(status().isAccepted());
        mvc.perform(get("/api/notifications").cookie(admin))
                .andExpect(jsonPath("$.unread").value(1))
                .andExpect(jsonPath("$.items[0].type").value("PASSWORD_HELP"))
                .andExpect(jsonPath("$.items[0].message").value(org.hamcrest.Matchers.containsString("bilkis@gmail.com")))
                .andExpect(jsonPath("$.items[0].message").value(org.hamcrest.Matchers.containsString("please help")));

        // Asking again soon after: a second request (with its own code) is stored, but the admin is not told twice...
        mvc.perform(withJson(post("/api/auth/password-help"), help)).andExpect(status().isAccepted());
        mvc.perform(get("/api/notifications").cookie(admin)).andExpect(jsonPath("$.items.length()").value(1));
        // ...after 15 minutes the admin is told again.
        mongo.updateMulti(new org.springframework.data.mongodb.core.query.Query(),
                org.springframework.data.mongodb.core.query.Update.update("createdAt", Instant.now().minusSeconds(20 * 60)),
                com.cancerweb.backend.model.PasswordResetRequest.class);
        mvc.perform(withJson(post("/api/auth/password-help"), help)).andExpect(status().isAccepted());
        mvc.perform(get("/api/notifications").cookie(admin)).andExpect(jsonPath("$.items.length()").value(2));
        // At most three open requests per account are kept; the visitor still gets a code.
        mvc.perform(withJson(post("/api/auth/password-help"), help))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.code").exists());
        org.assertj.core.api.Assertions.assertThat(mongo.count(new org.springframework.data.mongodb.core.query.Query(),
                com.cancerweb.backend.model.PasswordResetRequest.class)).isEqualTo(3);

        // Requests made before notifications existed get one at startup, dated when they were made; only once.
        notifications.deleteAll();
        passwordHelp.notifyAboutOpenRequestsWithoutNotification();
        passwordHelp.notifyAboutOpenRequestsWithoutNotification();
        mvc.perform(get("/api/notifications").cookie(admin))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].type").value("PASSWORD_HELP"));
        java.util.Set<Long> requestTimes = new java.util.HashSet<>();
        mongo.findAll(com.cancerweb.backend.model.PasswordResetRequest.class).forEach(r -> requestTimes.add(r.createdAt().toEpochMilli()));
        notifications.findAll().forEach(n -> org.assertj.core.api.Assertions.assertThat(requestTimes).contains(n.createdAt().toEpochMilli()));
    }

    @Test
    void profileUpdatesPasswordChangesAndMessagesCreateNotifications() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");

        // The user's own profile update.
        mvc.perform(withJson(put("/api/profile"), "{\"fullName\":\"Rahima Begum\",\"phone\":\"+8801712345678\"}").cookie(patient))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notifications").cookie(patient))
                .andExpect(jsonPath("$.items[0].type").value("PROFILE_UPDATED"))
                .andExpect(jsonPath("$.items[0].link").value("/profile"));
        // Saving the same details again is not news.
        mvc.perform(withJson(put("/api/profile"), "{\"fullName\":\"Rahima Begum\",\"phone\":\"+8801712345678\"}").cookie(patient));
        mvc.perform(get("/api/notifications").cookie(patient)).andExpect(jsonPath("$.items.length()").value(1));

        // A Contact message reaches the administrators, newest first.
        mvc.perform(withJson(post("/api/contact"), "{\"name\":\"Visitor\",\"email\":\"v@example.com\",\"subject\":\"Opening hours\","
                + "\"message\":\"When is the clinic open on Fridays?\"}")).andExpect(status().isCreated());
        mvc.perform(get("/api/notifications").cookie(admin))
                .andExpect(jsonPath("$.items[0].type").value("CONTACT_MESSAGE"))
                .andExpect(jsonPath("$.items[0].title").value("New message: Opening hours"))
                .andExpect(jsonPath("$.items[0].link").value("/admin?tab=messages"));

        // A new doctor registration and a password-help request also reach them.
        mvc.perform(withJson(post("/api/auth/register"), "{\"fullName\":\"Dr. Nasrin\",\"email\":\"n@example.com\",\"password\":\""
                + PASSWORD + "\",\"accountType\":\"DOCTOR\",\"doctorSpecialty\":\"BREAST_SURGEON\",\"confirmBreastSpecialist\":true}"))
                .andExpect(status().isCreated());
        mvc.perform(withJson(post("/api/auth/password-help"), "{\"email\":\"p@example.com\"}")).andExpect(status().isAccepted());
        mvc.perform(get("/api/notifications").cookie(admin))
                .andExpect(jsonPath("$.items[0].type").value("PASSWORD_HELP"))
                .andExpect(jsonPath("$.items[1].type").value("NEW_DOCTOR"))
                .andExpect(jsonPath("$.unread").value(3));

        // The administrator verifies the doctor: the doctor is told, with a link to their account page.
        mvc.perform(withJson(patch("/api/admin/users/" + id("n@example.com") + "/verification"), "{\"status\":\"VERIFIED\"}").cookie(admin))
                .andExpect(status().isOk());
        Cookie doctor = login("n@example.com", PASSWORD);
        mvc.perform(get("/api/notifications").cookie(doctor))
                .andExpect(jsonPath("$.items[0].type").value("ACCOUNT_VERIFIED"))
                .andExpect(jsonPath("$.items[0].link").value("/account-settings"));

        // Changing the password is recorded too.
        mvc.perform(withJson(put("/api/profile/password"), "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"BrandNew456\"}")
                .cookie(patient)).andExpect(status().isOk());
        Cookie again = login("p@example.com", "BrandNew456");
        mvc.perform(get("/api/notifications").cookie(again)).andExpect(jsonPath("$.items[0].type").value("PASSWORD_CHANGED"));
    }
}
