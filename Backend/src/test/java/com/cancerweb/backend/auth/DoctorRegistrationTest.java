package com.cancerweb.backend.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.mock.web.MockMultipartFile;

import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.repository.PredictionRepository;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/**
 * Registering as a doctor: the account starts PENDING, and patients can neither find it nor share reports with
 * it until an administrator verifies it (directly, or by approving its Breast Surgeon profile).
 */
class DoctorRegistrationTest extends IntegrationTest {

    private static final byte[] PDF = "%PDF-1.4\n% test certificate\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private UserRepository users;

    @Autowired
    private PredictionRepository predictions;

    @Autowired
    private GridFsTemplate gridFs;

    private static String registration(String name, String email, String accountType) {
        return "{\"fullName\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\""
                + (accountType == null ? "" : ",\"accountType\":\"" + accountType + "\"") + "}";
    }

    private String register(String name, String email, String accountType) throws Exception {
        String body = mvc.perform(withJson(post("/api/auth/register"), registration(name, email, accountType)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String scanOwnedBy(String ownerId) {
        byte[] image = { 1, 2, 3 };
        ObjectId imageId = gridFs.store(new ByteArrayInputStream(image), "scan.png", "image/png");
        return predictions.save(new Prediction(null, "scan.png", "image/png", image.length, imageId.toHexString(),
                "benign", 0.2, 0.8, "test", false, Instant.now(), ownerId, List.of(), null, "MLO", 0.5, false, null, null)).id();
    }

    @Test
    void registersAsPatientOrDoctorButNeverAdmin() throws Exception {
        mvc.perform(withJson(post("/api/auth/register"), registration("Ayesha Rahman", "ayesha@example.com", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"));
        mvc.perform(withJson(post("/api/auth/register"), registration("Dr. Nasrin Akter", "nasrin@example.com", "doctor")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("DOCTOR"))
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"));
        mvc.perform(withJson(post("/api/auth/register"), registration("Sneaky", "sneaky@example.com", "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.accountType").exists());
        org.assertj.core.api.Assertions.assertThat(users.findByEmail("sneaky@example.com")).isEmpty();

        // The new doctor can log in and reach the doctor pages straight away.
        Cookie doctor = login("nasrin@example.com", PASSWORD);
        mvc.perform(get("/api/auth/me").cookie(doctor)).andExpect(jsonPath("$.role").value("DOCTOR"));
        mvc.perform(get("/api/predictions/shared").cookie(doctor)).andExpect(status().isOk());
    }

    @Test
    void patientsCannotShareWithADoctorUntilAnAdministratorVerifiesThem() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        String doctorId = register("Dr. Nasrin Akter", "nasrin@example.com", "DOCTOR");
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        String scanId = scanOwnedBy(users.findByEmail("p@example.com").orElseThrow().id());
        String share = "{\"doctorId\":\"" + doctorId + "\"}";

        // Pending: not offered, and a share by id is refused.
        mvc.perform(get("/api/doctors").cookie(patient)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(withJson(post("/api/predictions/" + scanId + "/shares"), share).cookie(patient))
                .andExpect(status().isNotFound());

        // Verified: offered and shareable.
        mvc.perform(withJson(patch("/api/admin/users/" + doctorId + "/verification"), "{\"status\":\"VERIFIED\"}").cookie(admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/doctors").cookie(patient))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fullName").value("Dr. Nasrin Akter"));
        mvc.perform(withJson(post("/api/predictions/" + scanId + "/shares"), share).cookie(patient))
                .andExpect(status().isOk());

        // Rejected later: no longer offered, and no new shares.
        mvc.perform(withJson(patch("/api/admin/users/" + doctorId + "/verification"),
                        "{\"status\":\"REJECTED\",\"note\":\"Registration number does not match.\"}").cookie(admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/doctors").cookie(patient)).andExpect(jsonPath("$.length()").value(0));
        String other = scanOwnedBy(users.findByEmail("p@example.com").orElseThrow().id());
        mvc.perform(withJson(post("/api/predictions/" + other + "/shares"), share).cookie(patient))
                .andExpect(status().isNotFound());
    }

    @Test
    void approvingTheBreastSurgeonProfileVerifiesASelfRegisteredDoctor() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "a@example.com");
        register("Dr. Nasrin Akter", "nasrin@example.com", "DOCTOR");
        Cookie doctor = login("nasrin@example.com", PASSWORD);
        String profile = "{\"fullName\":\"Dr. Nasrin Akter\",\"email\":\"chamber@example.com\",\"phone\":\"+8801712345678\","
                + "\"designation\":\"Consultant Breast Surgeon\",\"qualifications\":\"MBBS, FCPS (Surgery)\","
                + "\"experienceYears\":12,\"hospitalName\":\"Dhaka Breast Centre\",\"hospitalAddress\":\"Dhanmondi, Dhaka\","
                + "\"consultationFee\":1500,\"biography\":\"Breast surgeon focused on early cancer care and reconstruction.\","
                + "\"registrationNumber\":\"BMDC-A-1234\",\"availableDays\":[\"MONDAY\"],\"timeSlots\":[\"09:00\"]}";
        String body = mvc.perform(withJson(post("/api/doctors/breast-surgeons/profile"), profile).cookie(doctor))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String profileId = JsonPath.read(body, "$.id");
        mvc.perform(multipart("/api/doctors/breast-surgeons/profile/documents")
                        .file(new MockMultipartFile("document", "registration.pdf", "application/pdf", PDF))
                        .with(csrf()).cookie(doctor))
                .andExpect(status().isOk());

        mvc.perform(withJson(patch("/api/admin/breast-surgeons/" + profileId + "/review"), "{\"status\":\"APPROVED\"}").cookie(admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(doctor)).andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));
        mvc.perform(get("/api/doctors/breast-surgeons")).andExpect(jsonPath("$.length()").value(1));

        // An account the administrator rejects leaves the listing, and its profile cannot be approved again.
        String doctorId = users.findByEmail("nasrin@example.com").orElseThrow().id();
        mvc.perform(withJson(patch("/api/admin/users/" + doctorId + "/verification"),
                        "{\"status\":\"REJECTED\",\"note\":\"Registration lapsed.\"}").cookie(admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/doctors/breast-surgeons")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(withJson(patch("/api/admin/breast-surgeons/" + profileId + "/review"), "{\"status\":\"APPROVED\"}").cookie(admin))
                .andExpect(status().isBadRequest());
    }
}
