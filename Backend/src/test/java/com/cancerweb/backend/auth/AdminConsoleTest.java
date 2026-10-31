package com.cancerweb.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.repository.PredictionRepository;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

/** The administrator's console: overview, user status, Contact inbox and website settings. */
class AdminConsoleTest extends IntegrationTest {

    private static final String SETTINGS = """
            {"contactPhone":"+880 1712-345678","contactEmail":"help@breastcare.org","contactAddress":"Dhaka, Bangladesh",
             "facebookUrl":"https://facebook.com/breastcare","youtubeUrl":"","linkedinUrl":"","twitterUrl":"",
             "heroVideoUrl":"https://www.youtube.com/watch?v=B4wzEGEYfnM","announcementText":"Free screening week!",
             "announcementEnabled":true}""";

    @Autowired
    private UserRepository users;

    @Autowired
    private PredictionRepository predictions;

    private void storePrediction(String ownerId, String label, Instant createdAt, List<String> sharedWith) {
        predictions.save(new Prediction(null, "secret-patient-scan.png", "image/png", 10, "000000000000000000000000",
                label, label.equals("malignant") ? 0.9 : 0.1, label.equals("malignant") ? 0.1 : 0.9, "v1", true,
                createdAt, ownerId, sharedWith, null, "CC", 0.5, false, null, null));
    }

    private void sendContactMessage(String subject) throws Exception {
        mvc.perform(withJson(post("/api/contact"), "{\"name\":\"Visitor\",\"email\":\"v@example.com\",\"subject\":\""
                + subject + "\",\"message\":\"Hello, I have a question.\"}")).andExpect(status().isCreated());
    }

    @Test
    void onlyAdminsReachTheConsole() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        Cookie doctor = signedIn(Role.DOCTOR, "d@example.com");
        for (String path : List.of("/api/admin/overview", "/api/admin/messages", "/api/admin/site-settings")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).cookie(patient)).andExpect(status().isForbidden());
            mvc.perform(get(path).cookie(doctor)).andExpect(status().isForbidden());
        }
        mvc.perform(withJson(put("/api/admin/site-settings"), SETTINGS).cookie(doctor)).andExpect(status().isForbidden());
    }

    @Test
    void overviewCountsTheWholeSiteWithoutExposingPatients() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        String patientId = createUser("Rahima Begum", "rahima@example.com", Role.PATIENT).id();
        String doctorId = createUser("Dr. Karim", "karim@example.com", Role.DOCTOR).id();
        createUser("Old Patient", "old@example.com", Role.PATIENT);
        mvc.perform(withJson(patch("/api/admin/users/" + users.findByEmail("old@example.com").orElseThrow().id() + "/status"),
                "{\"disabled\":true}").cookie(admin));

        Instant now = Instant.now();
        storePrediction(patientId, "benign", now, List.of());
        storePrediction(patientId, "malignant", now.minus(2, ChronoUnit.DAYS), List.of(doctorId));
        storePrediction(patientId, "benign", now.minus(20, ChronoUnit.DAYS), List.of());
        storePrediction(null, "benign", now.minus(40, ChronoUnit.DAYS), List.of());
        sendContactMessage("First");
        sendContactMessage("Second");
        // A message saved before read/unread existed.
        mongo.getCollection("contact_messages").insertOne(new Document("name", "Old").append("email", "o@example.com")
                .append("subject", "Old").append("message", "From before").append("createdAt", java.util.Date.from(now)));

        mvc.perform(get("/api/admin/overview").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.total").value(4))
                .andExpect(jsonPath("$.users.patients").value(2))
                .andExpect(jsonPath("$.users.doctors").value(1))
                .andExpect(jsonPath("$.users.admins").value(1))
                .andExpect(jsonPath("$.users.disabled").value(1))
                .andExpect(jsonPath("$.users.newLast7Days").value(4))
                .andExpect(jsonPath("$.predictions.total").value(4))
                .andExpect(jsonPath("$.predictions.last7Days").value(2))
                .andExpect(jsonPath("$.predictions.malignant").value(1))
                .andExpect(jsonPath("$.predictions.benign").value(3))
                .andExpect(jsonPath("$.predictions.demoMode").value(4))
                .andExpect(jsonPath("$.predictions.shared").value(1))
                .andExpect(jsonPath("$.predictions.withoutOwner").value(1))
                .andExpect(jsonPath("$.messages.total").value(3))
                .andExpect(jsonPath("$.messages.unread").value(3))
                .andExpect(jsonPath("$.system.databaseName").value(TEST_DATABASE))
                .andExpect(jsonPath("$.system.springBootVersion").isNotEmpty())
                .andExpect(jsonPath("$.system.model").isNotEmpty())
                .andExpect(jsonPath("$.uploadsLast14Days", hasSize(14)))
                .andExpect(jsonPath("$.uploadsLast14Days[13].date").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.uploadsLast14Days[13].count").value(1))
                // Totals only: no names, filenames or record ids of patients.
                .andExpect(content().string(not(containsString("Rahima"))))
                .andExpect(content().string(not(containsString("secret-patient-scan"))))
                .andExpect(content().string(not(containsString(patientId))));
    }

    // ---- Users ----

    @Test
    void disabledAccountsCannotLogInOrKeepUsingTheirLogin() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        String patientId = users.findByEmail("p@example.com").orElseThrow().id();

        mvc.perform(withJson(patch("/api/admin/users/" + patientId + "/status"), "{\"disabled\":true}").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disabled").value(true));

        mvc.perform(get("/api/auth/me").cookie(patient)).andExpect(status().isUnauthorized());
        mvc.perform(withJson(post("/api/auth/login"), "{\"email\":\"p@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("disabled")));
        // A wrong password still gets the usual answer, so guessing does not reveal that the account is disabled.
        mvc.perform(withJson(post("/api/auth/login"), "{\"email\":\"p@example.com\",\"password\":\"Wrong1234\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(withJson(patch("/api/admin/users/" + patientId + "/status"), "{\"disabled\":false}").cookie(admin))
                .andExpect(jsonPath("$.disabled").value(false));
        login("p@example.com", PASSWORD);
    }

    @Test
    void adminCannotDisableThemselvesAndStatusIsValidated() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        String adminId = users.findByEmail("a@example.com").orElseThrow().id();
        mvc.perform(withJson(patch("/api/admin/users/" + adminId + "/status"), "{\"disabled\":true}").cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You cannot disable your own account."));
        mvc.perform(withJson(patch("/api/admin/users/" + adminId + "/status"), "{}").cookie(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(withJson(patch("/api/admin/users/000000000000000000000000/status"), "{\"disabled\":true}").cookie(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void userListShowsStatusAndLastLogin() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        createUser("Never Logged In", "never@example.com", Role.PATIENT);
        mvc.perform(get("/api/admin/users").cookie(admin))
                .andExpect(jsonPath("$[0].email").value("never@example.com"))
                .andExpect(jsonPath("$[0].lastLoginAt").isEmpty())
                .andExpect(jsonPath("$[0].disabled").value(false))
                .andExpect(jsonPath("$[1].lastLoginAt").isNotEmpty())
                .andExpect(jsonPath("$[1].passwordHash").doesNotExist());
    }

    // ---- Contact inbox ----

    @Test
    void adminReadsFiltersMarksAndDeletesMessages() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        sendContactMessage("Older question");
        sendContactMessage("Newer question");

        String body = mvc.perform(get("/api/admin/messages").cookie(admin))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].subject").value("Newer question"))
                .andExpect(jsonPath("$[0].status").value("NEW"))
                .andReturn().getResponse().getContentAsString();
        String newest = com.jayway.jsonpath.JsonPath.read(body, "$[0].id");

        mvc.perform(withJson(patch("/api/admin/messages/" + newest), "{\"status\":\"READ\"}").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READ"));
        mvc.perform(get("/api/admin/messages?status=NEW").cookie(admin)).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/admin/messages?status=READ").cookie(admin))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(newest));

        mvc.perform(delete("/api/admin/messages/" + newest).cookie(admin).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/messages").cookie(admin)).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(delete("/api/admin/messages/" + newest).cookie(admin).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(withJson(patch("/api/admin/messages/" + newest), "{\"status\":\"READ\"}").cookie(admin))
                .andExpect(status().isNotFound());
    }

    // ---- Website settings ----

    @Test
    void siteSettingsStartUnconfiguredThenFollowTheAdmin() throws Exception {
        mvc.perform(get("/api/site-settings")).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false));

        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        mvc.perform(withJson(put("/api/admin/site-settings"), SETTINGS).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.updatedBy").value("a@example.com"));

        // Everyone, including visitors with a stale login cookie, sees the new values but not who changed them.
        mvc.perform(get("/api/site-settings").cookie(authCookie("expired.or.garbage")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value("help@breastcare.org"))
                .andExpect(jsonPath("$.facebookUrl").value("https://facebook.com/breastcare"))
                .andExpect(jsonPath("$.youtubeUrl").value(""))
                .andExpect(jsonPath("$.announcementEnabled").value(true))
                .andExpect(jsonPath("$.updatedBy").isEmpty());
    }

    @Test
    void siteSettingsRejectUnsafeOrInvalidValues() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        mvc.perform(withJson(put("/api/admin/site-settings"), """
                        {"contactPhone":"call us","contactEmail":"nope","facebookUrl":"javascript:alert(1)",
                         "twitterUrl":"https://x.com/a\\"onmouseover=\\"alert(1)","heroVideoUrl":"https://evil.example/video",
                         "announcementText":"","announcementEnabled":false}""").cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.contactPhone").exists())
                .andExpect(jsonPath("$.errors.contactEmail").exists())
                .andExpect(jsonPath("$.errors.facebookUrl").exists())
                .andExpect(jsonPath("$.errors.twitterUrl").exists())
                .andExpect(jsonPath("$.errors.heroVideoUrl").exists());
        mvc.perform(get("/api/site-settings")).andExpect(jsonPath("$.configured").value(false));

        // An announcement with no text stays off.
        mvc.perform(withJson(put("/api/admin/site-settings"), "{\"announcementText\":\"  \",\"announcementEnabled\":true}").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announcementEnabled").value(false));
        assertThat(mongo.getCollection("site_settings").countDocuments()).isEqualTo(1);
    }
}
