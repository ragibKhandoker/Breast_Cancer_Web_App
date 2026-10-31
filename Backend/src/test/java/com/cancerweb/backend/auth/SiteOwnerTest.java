package com.cancerweb.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

/** The site owner (ADMIN_EMAIL): their own Admin account page, and an account other administrators cannot touch. */
class SiteOwnerTest extends IntegrationTest {

    @Test
    void onlyTheOwnerOpensTheAdminAccountPage() throws Exception {
        String ownerId = createUser("Site Owner", OWNER_EMAIL, Role.ADMIN).id();
        Cookie owner = login(OWNER_EMAIL, PASSWORD);
        Cookie otherAdmin = signedIn(Role.ADMIN, "second-admin@example.com");
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");

        mvc.perform(get("/api/admin/account").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.id").value(ownerId))
                .andExpect(jsonPath("$.profile.email").value(OWNER_EMAIL))
                .andExpect(jsonPath("$.team.administrators").value(2))
                .andExpect(jsonPath("$.team.patients").value(1))
                .andExpect(jsonPath("$.activity.patientsVerified").value(0));
        mvc.perform(get("/api/admin/account").cookie(otherAdmin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Only the site owner can open this page."));
        mvc.perform(get("/api/admin/account").cookie(patient)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/account")).andExpect(status().isUnauthorized());

        // Login and /me tell the frontend who the owner is.
        mvc.perform(get("/api/auth/me").cookie(owner)).andExpect(jsonPath("$.owner").value(true));
        mvc.perform(get("/api/auth/me").cookie(otherAdmin)).andExpect(jsonPath("$.owner").value(false));
    }

    @Test
    void otherAdministratorsCannotTouchTheOwnersAccount() throws Exception {
        String ownerId = createUser("Site Owner", OWNER_EMAIL, Role.ADMIN).id();
        Cookie otherAdmin = signedIn(Role.ADMIN, "second-admin@example.com");

        mvc.perform(get("/api/admin/users/" + ownerId).cookie(otherAdmin)).andExpect(status().isForbidden());
        mvc.perform(withJson(patch("/api/admin/users/" + ownerId + "/status"), "{\"disabled\":true}").cookie(otherAdmin))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(patch("/api/admin/users/" + ownerId + "/role"), "{\"role\":\"PATIENT\"}").cookie(otherAdmin))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/admin/users/" + ownerId + "/password-reset"), "{}").cookie(otherAdmin))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").cookie(otherAdmin))
                .andExpect(jsonPath("$[?(@.id=='" + ownerId + "')].owner").value(true));

        // Nothing changed: the owner still logs in with the same password, as an administrator.
        Cookie owner = login(OWNER_EMAIL, PASSWORD);
        mvc.perform(get("/api/auth/me").cookie(owner)).andExpect(jsonPath("$.role").value("ADMIN"));

        // The owner still manages the other administrator.
        String otherId = userService.listAll().stream().filter(u -> !u.owner() && u.role() == Role.ADMIN).findFirst().orElseThrow().id();
        mvc.perform(withJson(patch("/api/admin/users/" + otherId + "/status"), "{\"disabled\":true}").cookie(owner))
                .andExpect(status().isOk());
    }

    @Test
    void theOwnersEmailStaysTheOneInTheSettings() throws Exception {
        createUser("Site Owner", OWNER_EMAIL, Role.ADMIN);
        Cookie owner = login(OWNER_EMAIL, PASSWORD);
        mvc.perform(withJson(put("/api/profile/email"), "{\"email\":\"new@example.com\",\"currentPassword\":\"" + PASSWORD + "\"}")
                        .cookie(owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void aPatientWithTheOwnersEmailIsNotTheOwner() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, OWNER_EMAIL);
        mvc.perform(get("/api/auth/me").cookie(patient)).andExpect(jsonPath("$.owner").value(false));
        mvc.perform(get("/api/admin/account").cookie(patient)).andExpect(status().isForbidden());
    }
}
