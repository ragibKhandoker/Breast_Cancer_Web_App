package com.cancerweb.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.cancerweb.backend.model.PasswordResetRequest;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/**
 * "Forgot password?" without login: the visitor's request code opens a status page with the administrator's
 * reply, and a temporary password the administrator sends after checking the person's identity, shown once.
 */
class PasswordHelpStatusTest extends IntegrationTest {

    private record Submitted(String code, String reference) {
    }

    private Submitted ask(String email) throws Exception {
        String body = mvc.perform(withJson(post("/api/auth/password-help"), "{\"email\":\"" + email + "\",\"message\":\"Locked out\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return new Submitted(JsonPath.read(body, "$.code"), JsonPath.read(body, "$.reference"));
    }

    private org.springframework.test.web.servlet.ResultActions statusOf(String code) throws Exception {
        return mvc.perform(withJson(post("/api/auth/password-help/status"), "{\"code\":\"" + code + "\"}"));
    }

    private String requestId(Cookie admin) throws Exception {
        String body = mvc.perform(get("/api/admin/password-requests?status=OPEN").cookie(admin)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[0].id");
    }

    @Test
    void unknownEmailsLookTheSameAsRealOnes() throws Exception {
        createUser("Bilkis Akter", "bilkis@gmail.com", Role.PATIENT);
        Submitted real = ask("bilkis@gmail.com");
        Submitted fake = ask("nobody@example.com");
        assertThat(real.code()).matches("[A-Z2-9]{5}(-[A-Z2-9]{5}){3}");
        assertThat(fake.code()).matches("[A-Z2-9]{5}(-[A-Z2-9]{5}){3}");
        assertThat(real.reference()).startsWith("PH-");
        statusOf(real.code()).andExpect(jsonPath("$.state").value("WAITING")).andExpect(jsonPath("$.reference").value(real.reference()));
        statusOf(fake.code()).andExpect(jsonPath("$.state").value("WAITING")).andExpect(jsonPath("$.reference").value(fake.reference()));
        // Typed by hand, in lower case and without dashes, the code still works.
        statusOf(real.code().toLowerCase().replace("-", "")).andExpect(jsonPath("$.reference").value(real.reference()));
        // Only the hash of the code is stored.
        PasswordResetRequest stored = mongo.findAll(PasswordResetRequest.class).getFirst();
        assertThat(stored.secretHash()).hasSize(64).doesNotContain(real.code());
        assertThat(stored.reference()).isEqualTo(real.reference());
    }

    @Test
    void adminRepliesThenSendsAPasswordThatIsShownOnceToThatRequestOnly() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        String userId = createUser("Bilkis Akter", "bilkis@gmail.com", Role.PATIENT).id();
        Submitted mine = ask("bilkis@gmail.com");
        String id = requestId(admin);
        // Someone else asks for the same account: their code must never get the password sent to mine.
        mongo.updateMulti(new Query(), Update.update("createdAt", Instant.now().minusSeconds(3600)), PasswordResetRequest.class);
        Submitted stranger = ask("bilkis@gmail.com");

        mvc.perform(get("/api/admin/password-requests?status=OPEN").cookie(admin))
                .andExpect(jsonPath("$[1].reference").value(mine.reference()))
                .andExpect(jsonPath("$[1].hasStatusPage").value(true))
                .andExpect(jsonPath("$[1].secretHash").doesNotExist());

        // A reply appears on the status page.
        mvc.perform(withJson(patch("/api/admin/password-requests/" + id + "/reply"), "{\"message\":\"We will call you on your phone.\"}")
                .cookie(admin)).andExpect(status().isOk());
        statusOf(mine.code()).andExpect(jsonPath("$.state").value("REPLIED")).andExpect(jsonPath("$.reply").value("We will call you on your phone."));

        // Sending a password needs the identity check.
        mvc.perform(withJson(post("/api/admin/password-requests/" + id + "/send-password"), "{\"identityConfirmed\":false}").cookie(admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.identityConfirmed").exists());
        String sent = mvc.perform(withJson(post("/api/admin/password-requests/" + id + "/send-password"),
                        "{\"identityConfirmed\":true,\"message\":\"Confirmed by phone. Log in and change it.\"}").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordSent").value(true))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andReturn().getResponse().getContentAsString();
        // The administrator never gets the password back, and the database holds it only sealed.
        assertThat(sent).doesNotContain("temporaryPassword");
        PasswordResetRequest stored = mongo.findById(id, PasswordResetRequest.class);
        assertThat(stored.deliveryCipher()).isNotBlank();

        // The stranger's request sees only that it was dealt with.
        statusOf(stranger.code()).andExpect(jsonPath("$.state").value("RESOLVED")).andExpect(jsonPath("$.temporaryPassword").doesNotExist());

        // The real user sees it once, logs in with it, and must choose a new password.
        String page = statusOf(mine.code())
                .andExpect(jsonPath("$.state").value("PASSWORD_READY"))
                .andExpect(jsonPath("$.reply").value("Confirmed by phone. Log in and change it."))
                .andReturn().getResponse().getContentAsString();
        String temporary = JsonPath.read(page, "$.temporaryPassword");
        assertThat(stored.deliveryCipher()).doesNotContain(temporary);
        statusOf(mine.code()).andExpect(jsonPath("$.state").value("PASSWORD_SHOWN")).andExpect(jsonPath("$.temporaryPassword").doesNotExist());
        assertThat(mongo.findById(id, PasswordResetRequest.class).deliveryCipher()).isNull();
        Cookie user = login("bilkis@gmail.com", temporary);
        mvc.perform(get("/api/auth/me").cookie(user)).andExpect(jsonPath("$.mustChangePassword").value(true));

        // The user's inbox explains what happened.
        mvc.perform(withJson(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/profile/password"),
                "{\"currentPassword\":\"" + temporary + "\",\"newPassword\":\"MyOwnPass9\"}").cookie(user)).andExpect(status().isOk());
        Cookie fresh = login("bilkis@gmail.com", "MyOwnPass9");
        mvc.perform(get("/api/messages").cookie(fresh))
                .andExpect(jsonPath("$.messages[0].senderName").value("BreastCare team"))
                .andExpect(jsonPath("$.messages[0].body").value(org.hamcrest.Matchers.containsString("Forgot password?")));
        assertThat(userId).isNotNull();
    }

    @Test
    void anExpiredDeliveryIsNotShown() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        createUser("Bilkis Akter", "bilkis@gmail.com", Role.PATIENT);
        Submitted mine = ask("bilkis@gmail.com");
        String id = requestId(admin);
        mvc.perform(withJson(post("/api/admin/password-requests/" + id + "/send-password"), "{\"identityConfirmed\":true}").cookie(admin))
                .andExpect(status().isOk());
        mongo.updateFirst(Query.query(org.springframework.data.mongodb.core.query.Criteria.where("id").is(id)),
                Update.update("deliveryExpiresAt", Instant.now().minusSeconds(5)), PasswordResetRequest.class);
        statusOf(mine.code()).andExpect(jsonPath("$.state").value("PASSWORD_EXPIRED")).andExpect(jsonPath("$.temporaryPassword").doesNotExist());
    }
}
