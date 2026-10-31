package com.cancerweb.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/**
 * "Forgot password?" answered by an administrator: the temporary password is stored only as a BCrypt hash and
 * shown once, it works for 24 hours, and until the user chooses their own password the account can do nothing
 * else. The same rules apply to patients and doctors.
 */
class TemporaryPasswordTest extends IntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String reset(Cookie admin, String userId) throws Exception {
        String body = mvc.perform(withJson(post("/api/admin/users/" + userId + "/password-reset"), "{}").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.temporaryPassword");
    }

    private static String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void passwordsAreStoredOnlyAsBcryptHashesAndNeverSentToTheAdministrator() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        String patientId = createUser("Rahima Begum", "rahima@example.com", Role.PATIENT).id();

        User stored = users.findById(patientId).orElseThrow();
        assertThat(stored.passwordHash()).startsWith("$2").doesNotContain(PASSWORD);
        String temporary = reset(admin, patientId);
        stored = users.findById(patientId).orElseThrow();
        assertThat(stored.passwordHash()).startsWith("$2").isNotEqualTo(temporary);
        assertThat(passwordEncoder.matches(temporary, stored.passwordHash())).isTrue();
        assertThat(stored.temporaryPasswordExpiresAt()).isBetween(Instant.now().plus(Duration.ofHours(23)), Instant.now().plus(Duration.ofHours(25)));

        // Nothing the administrator can call afterwards shows a password or its hash.
        for (String path : new String[] { "/api/admin/users", "/api/admin/users/" + patientId }) {
            String body = mvc.perform(get(path).cookie(admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(temporary).doesNotContain("passwordHash").doesNotContain("$2");
        }
    }

    @Test
    void onATemporaryPasswordTheUserCanOnlyChooseANewOne() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        for (Role role : new Role[] { Role.PATIENT, Role.DOCTOR }) {
            String email = role.name().toLowerCase() + "@example.com";
            String userId = createUser("Test User", email, role).id();
            String temporary = reset(admin, userId);
            Cookie user = login(email, temporary);

            // Allowed: who am I, my profile, change password, log out.
            mvc.perform(get("/api/auth/me").cookie(user)).andExpect(jsonPath("$.mustChangePassword").value(true));
            mvc.perform(get("/api/profile").cookie(user)).andExpect(status().isOk());
            // Everything else is refused until the password is changed.
            String blocked = role == Role.PATIENT ? "/api/predictions" : "/api/predictions/shared";
            mvc.perform(get(blocked).cookie(user))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("password_change_required"));
            mvc.perform(withJson(put("/api/profile"), "{\"fullName\":\"Renamed\"}").cookie(user))
                    .andExpect(status().isForbidden());

            // The new password must differ from the temporary one.
            mvc.perform(withJson(put("/api/profile/password"),
                            "{\"currentPassword\":\"" + temporary + "\",\"newPassword\":\"" + temporary + "\"}").cookie(user))
                    .andExpect(status().isBadRequest());
            Cookie fresh = mvc.perform(withJson(put("/api/profile/password"),
                            "{\"currentPassword\":\"" + temporary + "\",\"newPassword\":\"MyOwnPass9\"}").cookie(user))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mustChangePassword").value(false))
                    .andReturn().getResponse().getCookie("bc_auth");
            mvc.perform(get(blocked).cookie(fresh)).andExpect(status().isOk());
            assertThat(users.findById(userId).orElseThrow().temporaryPasswordExpiresAt()).isNull();

            // The temporary password is gone; the user's own one works.
            mvc.perform(withJson(post("/api/auth/login"), loginBody(email, temporary))).andExpect(status().isUnauthorized());
            login(email, "MyOwnPass9");
        }
    }

    @Test
    void anExpiredTemporaryPasswordNoLongerLogsIn() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        String patientId = createUser("Rahima Begum", "rahima@example.com", Role.PATIENT).id();
        String temporary = reset(admin, patientId);
        Cookie early = login("rahima@example.com", temporary);
        mongo.updateFirst(Query.query(Criteria.where("id").is(patientId)),
                Update.update("temporaryPasswordExpiresAt", Instant.now().minusSeconds(60)), User.class);

        mvc.perform(withJson(post("/api/auth/login"), loginBody("rahima@example.com", temporary)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("temporary_password_expired"));
        // A wrong password still gets the usual answer, so the expiry is only revealed to the right password.
        mvc.perform(withJson(post("/api/auth/login"), loginBody("rahima@example.com", "Wrong12345")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
        // A login made before the expiry stops working too.
        mvc.perform(get("/api/auth/me").cookie(early)).andExpect(status().isUnauthorized());

        // The user asks again; a new reset gives a fresh 24 hours.
        mvc.perform(withJson(post("/api/auth/password-help"), "{\"email\":\"rahima@example.com\"}")).andExpect(status().isAccepted());
        String again = reset(admin, patientId);
        login("rahima@example.com", again);
    }
}
