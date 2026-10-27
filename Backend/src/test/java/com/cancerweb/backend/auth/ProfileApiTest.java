package com.cancerweb.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

class ProfileApiTest extends IntegrationTest {

    private static final String FULL_PROFILE = """
            {"fullName":"  Ayesha Rahman ","phone":"+880 1712-345678","dateOfBirth":"1990-05-17",
             "gender":"FEMALE","address":"House 12, Road 5, Dhanmondi, Dhaka",
             "specialization":"Radiology","hospital":"City Hospital"}""";

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void profileRequiresLogin() throws Exception {
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
        mvc.perform(withJson(put("/api/profile"), FULL_PROFILE)).andExpect(status().isUnauthorized());
    }

    @Test
    void newAccountHasAnEmptyProfile() throws Exception {
        Cookie cookie = signedIn(Role.PATIENT, "p@example.com");
        mvc.perform(get("/api/profile").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("p@example.com"))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.phone").isEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void accountsFromBeforeProfilesExistStillLoad() throws Exception {
        Cookie cookie = signedIn(Role.PATIENT, "old@example.com");
        // Simulate a document written before the profile fields were added.
        mongo.getCollection("users").updateOne(new org.bson.Document("email", "old@example.com"),
                new org.bson.Document("$unset", new org.bson.Document("profile", "").append("passwordChangedAt", "")));
        mvc.perform(get("/api/profile").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.gender").isEmpty());
    }

    @Test
    void patientUpdatesTheirProfileAndItIsStored() throws Exception {
        Cookie cookie = signedIn(Role.PATIENT, "p@example.com");
        mvc.perform(withJson(put("/api/profile"), FULL_PROFILE).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ayesha Rahman"))
                .andExpect(jsonPath("$.phone").value("+880 1712-345678"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-05-17"))
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.address").value("House 12, Road 5, Dhanmondi, Dhaka"))
                // Doctor-only fields are not kept for patients.
                .andExpect(jsonPath("$.specialization").isEmpty())
                .andExpect(jsonPath("$.hospital").isEmpty());

        User stored = users.findByEmail("p@example.com").orElseThrow();
        assertThat(stored.fullName()).isEqualTo("Ayesha Rahman");
        assertThat(stored.profile().phone()).isEqualTo("+880 1712-345678");
        assertThat(stored.updatedAt()).isAfter(stored.createdAt());
        // The name shown elsewhere (header, doctor's list) follows.
        mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(jsonPath("$.fullName").value("Ayesha Rahman"));
    }

    @Test
    void doctorsKeepSpecializationAndHospital() throws Exception {
        Cookie cookie = signedIn(Role.DOCTOR, "d@example.com");
        mvc.perform(withJson(put("/api/profile"), FULL_PROFILE).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialization").value("Radiology"))
                .andExpect(jsonPath("$.hospital").value("City Hospital"));
    }

    @Test
    void blankOptionalFieldsClearTheValue() throws Exception {
        Cookie cookie = signedIn(Role.PATIENT, "p@example.com");
        mvc.perform(withJson(put("/api/profile"), FULL_PROFILE).cookie(cookie));
        mvc.perform(withJson(put("/api/profile"),
                        "{\"fullName\":\"Ayesha\",\"phone\":\"\",\"dateOfBirth\":\"\",\"gender\":\"\",\"address\":\"  \"}").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").isEmpty())
                .andExpect(jsonPath("$.address").isEmpty());
        assertThat(users.findByEmail("p@example.com").orElseThrow().profile().dateOfBirth()).isNull();
    }

    @Test
    void invalidValuesAreRejectedPerField() throws Exception {
        Cookie cookie = signedIn(Role.PATIENT, "p@example.com");
        mvc.perform(withJson(put("/api/profile"),
                        "{\"fullName\":\"\",\"phone\":\"call me\",\"dateOfBirth\":\"17/05/1990\",\"gender\":\"ROBOT\",\"address\":\""
                                + "x".repeat(301) + "\"}").cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").value("Full name is required"))
                .andExpect(jsonPath("$.errors.phone").exists())
                .andExpect(jsonPath("$.errors.dateOfBirth").value("Enter the date as YYYY-MM-DD"))
                .andExpect(jsonPath("$.errors.gender").exists())
                .andExpect(jsonPath("$.errors.address").exists());

        String tomorrow = LocalDate.now().plusDays(1).toString();
        mvc.perform(withJson(put("/api/profile"), "{\"fullName\":\"Ayesha\",\"dateOfBirth\":\"" + tomorrow + "\"}").cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dateOfBirth").value("Date of birth must be in the past"));
        mvc.perform(withJson(put("/api/profile"), "{\"fullName\":\"Ayesha\",\"dateOfBirth\":\"1990-02-30\"}").cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dateOfBirth").value("Enter a real date"));
    }

    @Test
    void profileUpdateCannotChangeRoleEmailOrPassword() throws Exception {
        Cookie cookie = signedIn(Role.PATIENT, "p@example.com");
        String hashBefore = users.findByEmail("p@example.com").orElseThrow().passwordHash();
        mvc.perform(withJson(put("/api/profile"), """
                        {"fullName":"Sneaky","role":"ADMIN","email":"admin@example.com","passwordHash":"x","id":"other"}""")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.email").value("p@example.com"));
        User stored = users.findByEmail("p@example.com").orElseThrow();
        assertThat(stored.role()).isEqualTo(Role.PATIENT);
        assertThat(stored.passwordHash()).isEqualTo(hashBefore);
    }

    @Test
    void profileEditAndAdminRoleChangeDoNotOverwriteEachOther() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        String patientId = users.findByEmail("p@example.com").orElseThrow().id();
        mvc.perform(withJson(put("/api/profile"), FULL_PROFILE).cookie(patient));
        mvc.perform(withJson(patch("/api/admin/users/" + patientId + "/role"), "{\"role\":\"DOCTOR\"}").cookie(admin))
                .andExpect(status().isOk());
        User stored = users.findById(patientId).orElseThrow();
        assertThat(stored.role()).isEqualTo(Role.DOCTOR);
        assertThat(stored.profile().phone()).isEqualTo("+880 1712-345678");
    }

    // ---- Email ----

    @Test
    void changeEmailNeedsTheCurrentPassword() throws Exception {
        Cookie cookie = signedIn(Role.PATIENT, "p@example.com");
        mvc.perform(withJson(put("/api/profile/email"), "{\"email\":\"new@example.com\",\"currentPassword\":\"Wrong1234\"}").cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").value("Your current password is incorrect"));

        mvc.perform(withJson(put("/api/profile/email"), "{\"email\":\" New@Example.com \",\"currentPassword\":\"" + PASSWORD + "\"}").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"));
        login("new@example.com", PASSWORD);
        assertThat(users.findByEmail("p@example.com")).isEmpty();
    }

    @Test
    void changeEmailRejectsAnAddressInUse() throws Exception {
        createUser("Taken", "taken@example.com", Role.PATIENT);
        Cookie cookie = signedIn(Role.PATIENT, "p@example.com");
        mvc.perform(withJson(put("/api/profile/email"), "{\"email\":\"TAKEN@example.com\",\"currentPassword\":\"" + PASSWORD + "\"}").cookie(cookie))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.email").value("An account with this email already exists."));
        mvc.perform(withJson(put("/api/profile/email"), "{\"email\":\"not-an-email\",\"currentPassword\":\"" + PASSWORD + "\"}").cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    // ---- Password ----

    @Test
    void changePasswordKeepsThisBrowserSignedInAndSignsOutOthers() throws Exception {
        createUser("Pat", "p@example.com", Role.PATIENT);
        Cookie thisBrowser = login("p@example.com", PASSWORD);
        // A login from another device, issued a few seconds earlier.
        String userId = users.findByEmail("p@example.com").orElseThrow().id();
        Cookie otherDevice = authCookie(signToken(JWT_SECRET, userId, Instant.now().minusSeconds(30), Instant.now().plusSeconds(3600)));
        mvc.perform(get("/api/auth/me").cookie(otherDevice)).andExpect(status().isOk());

        Cookie fresh = mvc.perform(withJson(put("/api/profile/password"),
                        "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"BrandNew456\"}").cookie(thisBrowser))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andReturn().getResponse().getCookie("bc_auth");

        mvc.perform(get("/api/auth/me").cookie(fresh)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(thisBrowser)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(otherDevice)).andExpect(status().isUnauthorized());

        User stored = users.findByEmail("p@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("BrandNew456", stored.passwordHash())).isTrue();
        login("p@example.com", "BrandNew456");
    }

    @Test
    void changePasswordValidatesInput() throws Exception {
        Cookie cookie = signedIn(Role.PATIENT, "p@example.com");
        mvc.perform(withJson(put("/api/profile/password"), "{\"currentPassword\":\"Wrong1234\",\"newPassword\":\"BrandNew456\"}").cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").exists());
        mvc.perform(withJson(put("/api/profile/password"), "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"weak\"}").cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
        mvc.perform(withJson(put("/api/profile/password"), "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + PASSWORD + "\"}").cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").value("Choose a password different from your current one"));
        // Nothing changed, so the old login still works.
        mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk());
    }
}
