package com.cancerweb.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.cancerweb.backend.model.ResetRequestStatus;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.PasswordResetRequestRepository;
import com.cancerweb.backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** The admin portal: checking patients' details, and resetting passwords from "Forgot password?" requests. */
class AdminPortalTest extends IntegrationTest {

    @Autowired
    private PasswordResetRequestRepository resetRequests;

    private static String profileBody(String name, String phone) {
        return "{\"fullName\":\"" + name + "\",\"phone\":\"" + phone + "\",\"dateOfBirth\":\"1990-05-01\","
                + "\"gender\":\"FEMALE\",\"address\":\"Dhaka\",\"specialization\":\"\",\"hospital\":\"\"}";
    }

    @Test
    void onlyAdminsUseThePortal() throws Exception {
        String patientId = createUser("Rahima Begum", "rahima@example.com", Role.PATIENT).id();
        Cookie doctor = signedIn(Role.DOCTOR, "d@example.com");
        Cookie patient = login("rahima@example.com", PASSWORD);
        for (Cookie cookie : List.of(doctor, patient)) {
            mvc.perform(get("/api/admin/users/" + patientId).cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/password-requests").cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(withJson(patch("/api/admin/users/" + patientId + "/verification"), "{\"status\":\"VERIFIED\"}")
                    .cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(withJson(post("/api/admin/users/" + patientId + "/password-reset"), "{}").cookie(cookie))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminChecksAndVerifiesAPatient() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        String patientId = createUser("Rahima Begum", "rahima@example.com", Role.PATIENT).id();
        String doctorId = createUser("Dr. Karim", "karim@example.com", Role.DOCTOR).id();
        Cookie patient = login("rahima@example.com", PASSWORD);
        mvc.perform(withJson(put("/api/profile"), profileBody("Rahima Begum", "+8801712345678")).cookie(patient))
                .andExpect(status().isOk());

        // New patients wait for review.
        mvc.perform(get("/api/profile").cookie(patient)).andExpect(jsonPath("$.verificationStatus").value("PENDING"));
        mvc.perform(get("/api/admin/users").cookie(admin))
                .andExpect(jsonPath("$[?(@.id=='" + patientId + "')].verificationStatus").value("PENDING"));
        mvc.perform(get("/api/admin/overview").cookie(admin))
                .andExpect(jsonPath("$.support.pendingVerification").value(1))
                .andExpect(jsonPath("$.support.verified").value(0));

        // The admin sees the private details, but never the password hash.
        mvc.perform(get("/api/admin/users/" + patientId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+8801712345678"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-05-01"))
                .andExpect(jsonPath("$.predictionCount").value(0))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // A rejection must say what to fix, and the patient sees that note.
        mvc.perform(withJson(patch("/api/admin/users/" + patientId + "/verification"), "{\"status\":\"REJECTED\"}").cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.note").exists());
        mvc.perform(withJson(patch("/api/admin/users/" + patientId + "/verification"),
                        "{\"status\":\"REJECTED\",\"note\":\"Please add your full date of birth.\"}").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.verifiedBy").value("a@example.com"));
        mvc.perform(get("/api/profile").cookie(patient))
                .andExpect(jsonPath("$.verificationStatus").value("REJECTED"))
                .andExpect(jsonPath("$.verificationNote").value("Please add your full date of birth."));

        mvc.perform(withJson(patch("/api/admin/users/" + patientId + "/verification"), "{\"status\":\"VERIFIED\"}").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.verificationNote").doesNotExist());
        mvc.perform(get("/api/auth/me").cookie(patient)).andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
        mvc.perform(get("/api/admin/overview").cookie(admin))
                .andExpect(jsonPath("$.support.pendingVerification").value(0))
                .andExpect(jsonPath("$.support.verified").value(1));

        // Saving the same details keeps the check; changing them sends the patient back for review.
        mvc.perform(withJson(put("/api/profile"), profileBody("Rahima Begum", "+8801712345678")).cookie(patient))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
        mvc.perform(withJson(put("/api/profile"), profileBody("Rahima Begum", "+8801812345678")).cookie(patient))
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"));

        // A doctor made by an administrator counts as verified; moving it back to pending is stored, not dropped.
        mvc.perform(get("/api/admin/users/" + doctorId).cookie(admin)).andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
        mvc.perform(withJson(patch("/api/admin/users/" + doctorId + "/verification"), "{\"status\":\"PENDING\"}").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"));
        mvc.perform(withJson(patch("/api/admin/users/" + doctorId + "/verification"), "{\"status\":\"VERIFIED\"}").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
        // Administrators are never verified.
        String adminId = mongo.findOne(Query.query(Criteria.where("email").is("a@example.com")), User.class).id();
        mvc.perform(withJson(patch("/api/admin/users/" + adminId + "/verification"), "{\"status\":\"VERIFIED\"}").cookie(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/users/000000000000000000000000").cookie(admin)).andExpect(status().isNotFound());
    }

    @Test
    void forgotPasswordRequestsDoNotRevealWhichEmailsExist() throws Exception {
        createUser("Rahima Begum", "rahima@example.com", Role.PATIENT);
        String unknown = mvc.perform(withJson(post("/api/auth/password-help"), "{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String known = mvc.perform(withJson(post("/api/auth/password-help"),
                        "{\"email\":\" Rahima@Example.com \",\"message\":\"I forgot it after my holiday.\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        // Same message, and a request code of the same form either way; only the random code differs.
        assertThat((String) JsonPath.read(known, "$.message")).isEqualTo(JsonPath.read(unknown, "$.message"));
        assertThat((String) JsonPath.read(known, "$.code")).matches("[A-Z2-9]{5}(-[A-Z2-9]{5}){3}");
        assertThat((String) JsonPath.read(unknown, "$.code")).matches("[A-Z2-9]{5}(-[A-Z2-9]{5}){3}");
        assertThat((String) JsonPath.read(known, "$.code")).isNotEqualTo(JsonPath.read(unknown, "$.code"));

        // Only the request for the real account is stored.
        assertThat(resetRequests.findAll()).singleElement().satisfies(r -> {
            assertThat(r.email()).isEqualTo("rahima@example.com");
            assertThat(r.message()).isEqualTo("I forgot it after my holiday.");
            assertThat(r.status()).isEqualTo(ResetRequestStatus.OPEN);
        });

        mvc.perform(withJson(post("/api/auth/password-help"), "{\"email\":\"not-an-email\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void adminResetsAPasswordAndTheUserMustChooseANewOne() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        String adminId = userService.listAll().getFirst().id();
        String patientId = createUser("Rahima Begum", "rahima@example.com", Role.PATIENT).id();
        mvc.perform(withJson(post("/api/auth/password-help"), "{\"email\":\"rahima@example.com\"}")).andExpect(status().isAccepted());
        mvc.perform(get("/api/admin/password-requests?status=OPEN").cookie(admin))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(patientId))
                .andExpect(jsonPath("$[0].fullName").value("Rahima Begum"));
        mvc.perform(get("/api/admin/overview").cookie(admin)).andExpect(jsonPath("$.support.openPasswordRequests").value(1));

        String body = mvc.perform(withJson(post("/api/admin/users/" + patientId + "/password-reset"), "{}").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();
        String temporary = JsonPath.read(body, "$.temporaryPassword");
        assertThat(temporary).hasSize(12).matches(".*[A-Z].*").matches(".*[a-z].*").matches(".*\\d.*");

        // The old password no longer works; the temporary one does, and asks for a new password.
        mvc.perform(withJson(post("/api/auth/login"), "{\"email\":\"rahima@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
        Cookie patient = login("rahima@example.com", temporary);
        mvc.perform(get("/api/profile").cookie(patient)).andExpect(jsonPath("$.mustChangePassword").value(true));

        // The open request was resolved by the reset.
        mvc.perform(get("/api/admin/password-requests").cookie(admin))
                .andExpect(jsonPath("$[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$[0].handledBy").value("a@example.com"));

        mvc.perform(withJson(put("/api/profile/password"),
                        "{\"currentPassword\":\"" + temporary + "\",\"newPassword\":\"MyOwnPass9\"}").cookie(patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        // An admin changes their own password on the profile page, not here.
        mvc.perform(withJson(post("/api/admin/users/" + adminId + "/password-reset"), "{}").cookie(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminDismissesARequest() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        createUser("Rahima Begum", "rahima@example.com", Role.PATIENT);
        mvc.perform(withJson(post("/api/auth/password-help"), "{\"email\":\"rahima@example.com\"}")).andExpect(status().isAccepted());
        String id = resetRequests.findAll().getFirst().id();

        mvc.perform(withJson(patch("/api/admin/password-requests/" + id), "{\"status\":\"DISMISSED\"}").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"))
                .andExpect(jsonPath("$.handledBy").value("a@example.com"));
        mvc.perform(get("/api/admin/password-requests?status=OPEN").cookie(admin)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(withJson(patch("/api/admin/password-requests/000000000000000000000000"), "{\"status\":\"DISMISSED\"}")
                .cookie(admin)).andExpect(status().isNotFound());
    }
}
