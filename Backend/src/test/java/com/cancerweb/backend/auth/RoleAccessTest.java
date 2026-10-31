package com.cancerweb.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.model.User;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

/** Which role may call which endpoint. Per-record checks are in RecordOwnershipTest. */
class RoleAccessTest extends IntegrationTest {

    @Autowired
    private UserRepository users;

    @Test
    void anonymousVisitorsGet401OnPrivateApis() throws Exception {
        for (String path : new String[] { "/api/predictions", "/api/predictions/shared", "/api/admin/users",
                "/api/doctors", "/api/auth/me", "/api/predictions/abc/image", "/api/predictions/abc/heatmap",
                "/api/scans/abc/report" }) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void publicPagesAndHealthStayOpen() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(withJson(post("/api/contact"),
                "{\"name\":\"Visitor\",\"email\":\"v@example.com\",\"subject\":\"Hello\",\"message\":\"Just saying hello.\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void onlyAdminsReachUserManagement() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        Cookie doctor = signedIn(Role.DOCTOR, "d@example.com");
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");

        mvc.perform(get("/api/admin/users").cookie(patient))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You do not have permission to do this."));
        mvc.perform(get("/api/admin/users").cookie(doctor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mvc.perform(get("/actuator/info").cookie(patient)).andExpect(status().isForbidden());
    }

    @Test
    void onlyPatientsUploadAndListTheirScans() throws Exception {
        Cookie doctor = signedIn(Role.DOCTOR, "d@example.com");
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        MockMultipartFile image = new MockMultipartFile("image", "scan.png", "image/png", new byte[] { 1, 2, 3 });

        mvc.perform(multipart("/api/predictions").file(image).cookie(doctor).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/predictions").file(image).cookie(admin).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/predictions").cookie(doctor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/predictions/some-id").cookie(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/doctors").cookie(doctor)).andExpect(status().isForbidden());
    }

    @Test
    void onlyDoctorsListSharedReports() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        Cookie doctor = signedIn(Role.DOCTOR, "d@example.com");
        mvc.perform(get("/api/predictions/shared").cookie(patient)).andExpect(status().isForbidden());
        mvc.perform(get("/api/predictions/shared").cookie(doctor)).andExpect(status().isOk());
    }

    @Test
    void adminCreatesDoctorAccounts() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        mvc.perform(withJson(post("/api/admin/users"),
                        "{\"fullName\":\"Dr. Karim\",\"email\":\"Karim@Clinic.org\",\"password\":\"" + PASSWORD + "\",\"role\":\"DOCTOR\"}")
                        .cookie(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("DOCTOR"))
                .andExpect(jsonPath("$.email").value("karim@clinic.org"));
        login("karim@clinic.org", PASSWORD);

        mvc.perform(withJson(post("/api/admin/users"),
                        "{\"fullName\":\"Dup\",\"email\":\"karim@clinic.org\",\"password\":\"" + PASSWORD + "\",\"role\":\"DOCTOR\"}")
                        .cookie(admin))
                .andExpect(status().isConflict());
        mvc.perform(withJson(post("/api/admin/users"),
                        "{\"fullName\":\"No Role\",\"email\":\"norole@clinic.org\",\"password\":\"" + PASSWORD + "\"}")
                        .cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").exists());
    }

    @Test
    void patientsCannotCreateAccountsOrChangeRoles() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        User self = users.findByEmail("p@example.com").orElseThrow();
        mvc.perform(withJson(post("/api/admin/users"),
                        "{\"fullName\":\"Evil\",\"email\":\"evil@example.com\",\"password\":\"" + PASSWORD + "\",\"role\":\"ADMIN\"}")
                        .cookie(patient))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(patch("/api/admin/users/" + self.id() + "/role"), "{\"role\":\"ADMIN\"}").cookie(patient))
                .andExpect(status().isForbidden());
        assertThat(users.findById(self.id()).orElseThrow().role()).isEqualTo(Role.PATIENT);
    }

    @Test
    void roleChangeAppliesToTheUsersNextRequest() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        String patientId = users.findByEmail("p@example.com").orElseThrow().id();
        mvc.perform(get("/api/predictions").cookie(patient)).andExpect(status().isOk());

        mvc.perform(withJson(patch("/api/admin/users/" + patientId + "/role"), "{\"role\":\"DOCTOR\"}").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DOCTOR"));

        // Same cookie, issued while they were a patient: the role is read from the database on every request.
        mvc.perform(get("/api/predictions").cookie(patient)).andExpect(status().isForbidden());
        mvc.perform(get("/api/predictions/shared").cookie(patient)).andExpect(status().isOk());
    }

    @Test
    void adminCannotChangeOwnRoleSoThereIsAlwaysAnAdmin() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        String adminId = users.findByEmail("a@example.com").orElseThrow().id();
        mvc.perform(withJson(patch("/api/admin/users/" + adminId + "/role"), "{\"role\":\"PATIENT\"}").cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You cannot change your own role."));
        mvc.perform(withJson(patch("/api/admin/users/000000000000000000000000/role"), "{\"role\":\"DOCTOR\"}").cookie(admin))
                .andExpect(status().isNotFound());
    }
}
