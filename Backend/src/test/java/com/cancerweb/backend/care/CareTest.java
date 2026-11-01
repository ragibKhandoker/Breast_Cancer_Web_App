package com.cancerweb.backend.care;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.mock.web.MockMultipartFile;

import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.repository.PredictionRepository;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** Doctor-patient care: the relation from appointments, doctor-run analyses, and report review requests. */
class CareTest extends IntegrationTest {

    private static final byte[] PDF = "%PDF-1.4\n% test certificate\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] IMAGE = { 9, 8, 7, 6 };

    @Autowired
    private UserRepository users;

    @Autowired
    private PredictionRepository predictions;

    @Autowired
    private GridFsTemplate gridFs;

    @Value("${ml.model.path}")
    private String modelPath;

    private Cookie admin;
    private Cookie doctor;
    private Cookie otherDoctor;
    private Cookie alice;
    private Cookie bob;
    private String doctorId;
    private String otherDoctorId;
    private String aliceId;
    private String surgeonProfileId;
    private Prediction aliceScan;

    @BeforeEach
    void setUp() throws Exception {
        admin = signedIn(Role.ADMIN, "admin@example.com");
        doctor = signedIn(Role.DOCTOR, "doctor@example.com");
        otherDoctor = signedIn(Role.DOCTOR, "other.doctor@example.com");
        alice = signedIn(Role.PATIENT, "alice@example.com");
        bob = signedIn(Role.PATIENT, "bob@example.com");
        doctorId = users.findByEmail("doctor@example.com").orElseThrow().id();
        otherDoctorId = users.findByEmail("other.doctor@example.com").orElseThrow().id();
        aliceId = users.findByEmail("alice@example.com").orElseThrow().id();
        surgeonProfileId = approvedSurgeon(doctor, "Dr. Nasrin Akter", "BMDC-A-1234");
        approvedSurgeon(otherDoctor, "Dr. Karim", "BMDC-A-5678");
        aliceScan = store(aliceId);
    }

    private String approvedSurgeon(Cookie account, String name, String registration) throws Exception {
        String profile = "{\"fullName\":\"" + name + "\",\"email\":\"chamber@example.com\",\"phone\":\"+8801712345678\","
                + "\"designation\":\"Consultant Breast Surgeon\",\"qualifications\":\"MBBS, FCPS (Surgery)\","
                + "\"experienceYears\":12,\"hospitalName\":\"Dhaka Breast Centre\",\"hospitalAddress\":\"Dhanmondi, Dhaka\","
                + "\"consultationFee\":1500,\"biography\":\"Breast surgeon focused on early cancer care and reconstruction.\","
                + "\"registrationNumber\":\"" + registration + "\","
                + "\"availableDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\",\"SUNDAY\"],"
                + "\"timeSlots\":[\"09:00\",\"10:00\"]}";
        String body = mvc.perform(withJson(post("/api/doctors/breast-surgeons/profile"), profile).cookie(account))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        mvc.perform(multipart("/api/doctors/breast-surgeons/profile/documents")
                        .file(new MockMultipartFile("document", "registration.pdf", "application/pdf", PDF)).with(csrf()).cookie(account))
                .andExpect(status().isOk());
        mvc.perform(withJson(patch("/api/admin/breast-surgeons/" + id + "/review"), "{\"status\":\"APPROVED\"}").cookie(admin))
                .andExpect(status().isOk());
        return id;
    }

    /** Alice books the doctor; the doctor confirms when {@code confirm} is true. */
    private void appointment(boolean confirm) throws Exception {
        String tomorrow = LocalDate.now(ZoneId.of("Asia/Dhaka")).plusDays(1).toString();
        String body = mvc.perform(withJson(post("/api/appointments"),
                        "{\"surgeonId\":\"" + surgeonProfileId + "\",\"date\":\"" + tomorrow + "\",\"time\":\"09:00\",\"reason\":\"Lump check\"}")
                        .cookie(alice))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        if (confirm) {
            mvc.perform(withJson(put("/api/appointments/" + JsonPath.read(body, "$.id") + "/confirm"), "{}").cookie(doctor))
                    .andExpect(status().isOk());
        }
    }

    private Prediction store(String ownerId) {
        ObjectId imageId = gridFs.store(new ByteArrayInputStream(IMAGE), "scan.png", "image/png");
        return predictions.save(new Prediction(null, "scan.png", "image/png", IMAGE.length, imageId.toHexString(),
                "benign", 0.2, 0.8, "test", false, Instant.now(), ownerId, List.of(), null, "MLO", 0.5, false, null, null));
    }

    private String requestBody(String doctor, String message) {
        return "{\"predictionId\":\"" + aliceScan.id() + "\",\"doctorId\":\"" + doctor + "\",\"message\":\"" + message + "\"}";
    }

    @Test
    void aConfirmedAppointmentMakesSomeoneTheDoctorsPatient() throws Exception {
        appointment(false);
        // A request the doctor has not confirmed yet is not a care relation.
        mvc.perform(get("/api/care/my-patients").cookie(doctor)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/care/my-doctors").cookie(alice)).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/appointments/doctor").cookie(doctor)).andExpect(status().isOk());
        String appointmentId = JsonPath.read(mvc.perform(get("/api/appointments/my").cookie(alice))
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        mvc.perform(withJson(put("/api/appointments/" + appointmentId + "/confirm"), "{}").cookie(doctor)).andExpect(status().isOk());

        mvc.perform(get("/api/care/my-patients").cookie(doctor))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(aliceId))
                .andExpect(jsonPath("$[0].fullName").value("Patient User"))
                .andExpect(jsonPath("$[0].lastVisitStatus").value("CONFIRMED"));
        mvc.perform(get("/api/care/my-doctors").cookie(alice))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(doctorId));
        mvc.perform(get("/api/care/my-patients").cookie(otherDoctor)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rolesAreChecked() throws Exception {
        mvc.perform(get("/api/care/my-patients").cookie(alice)).andExpect(status().isForbidden());
        mvc.perform(get("/api/care/review-inbox").cookie(alice)).andExpect(status().isForbidden());
        mvc.perform(get("/api/care/my-doctors").cookie(doctor)).andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/care/review-requests"), requestBody(doctorId, "")).cookie(doctor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/care/my-doctors").cookie(admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/care/my-patients")).andExpect(status().isUnauthorized());
    }

    @Test
    void doctorsOnlySeeTheirOwnPatientsScans() throws Exception {
        appointment(true);
        mvc.perform(get("/api/care/my-patients/" + aliceId + "/scans").cookie(otherDoctor)).andExpect(status().isForbidden());
        // Not shared yet: the doctor sees none of Alice's reports.
        mvc.perform(get("/api/care/my-patients/" + aliceId + "/scans").cookie(doctor)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/predictions/" + aliceScan.id()).cookie(doctor)).andExpect(status().isNotFound());

        // Another doctor cannot run an analysis for someone who is not their patient.
        mvc.perform(multipart("/api/care/my-patients/" + aliceId + "/scans")
                        .file(new MockMultipartFile("image", "m.png", "image/png", IMAGE)).with(csrf()).cookie(otherDoctor))
                .andExpect(status().isForbidden());
        assertThat(predictions.count()).isEqualTo(1);
    }

    @Test
    void patientsAskTheirDoctorForAReviewAndSeeTheAnswer() throws Exception {
        appointment(true);
        // Only a doctor the patient has seen, and only their own report.
        mvc.perform(withJson(post("/api/care/review-requests"), requestBody(otherDoctorId, "")).cookie(alice))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/care/review-requests"), requestBody(doctorId, "")).cookie(bob))
                .andExpect(status().isNotFound());

        String created = mvc.perform(withJson(post("/api/care/review-requests"), requestBody(doctorId, "Is this normal?")).cookie(alice))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.doctorName").value("Doctor User"))
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(created, "$.id");
        mvc.perform(withJson(post("/api/care/review-requests"), requestBody(doctorId, "again")).cookie(alice))
                .andExpect(status().isConflict());

        // The request shares the report, so the doctor can open it and finds it in the inbox.
        mvc.perform(get("/api/predictions/" + aliceScan.id()).cookie(doctor)).andExpect(status().isOk());
        mvc.perform(get("/api/care/review-inbox").cookie(doctor))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].message").value("Is this normal?"));
        mvc.perform(get("/api/care/my-patients").cookie(doctor)).andExpect(jsonPath("$[0].openRequests").value(1));

        mvc.perform(withJson(put("/api/care/review-inbox/" + requestId), "{\"note\":\"\"}").cookie(doctor))
                .andExpect(status().isBadRequest());
        mvc.perform(withJson(put("/api/care/review-inbox/" + requestId), "{\"note\":\"Looks fine\"}").cookie(otherDoctor))
                .andExpect(status().isNotFound());
        mvc.perform(withJson(put("/api/care/review-inbox/" + requestId),
                        "{\"note\":\"The scan looks reassuring. Keep your yearly screening.\"}").cookie(doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWED"));

        mvc.perform(get("/api/care/reports/" + aliceScan.id() + "/reviews").cookie(alice))
                .andExpect(jsonPath("$[0].doctorNote").value("The scan looks reassuring. Keep your yearly screening."));
        mvc.perform(get("/api/care/reports/" + aliceScan.id() + "/reviews").cookie(bob)).andExpect(status().isNotFound());
        assertThat(mongo.count(Query.query(Criteria.where("userId").is(aliceId).and("type").is("REPORT_REVIEWED")), "notifications"))
                .isEqualTo(1);
        assertThat(mongo.count(Query.query(Criteria.where("userId").is(doctorId).and("type").is("REVIEW_REQUESTED")), "notifications"))
                .isEqualTo(1);
    }

    @Test
    void unsharingWithdrawsAndDeletingRemovesRequests() throws Exception {
        appointment(true);
        mvc.perform(withJson(post("/api/care/review-requests"), requestBody(doctorId, "")).cookie(alice)).andExpect(status().isCreated());
        mvc.perform(delete("/api/predictions/" + aliceScan.id() + "/shares/" + doctorId).with(csrf()).cookie(alice))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/care/review-inbox").cookie(doctor)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/care/review-requests").cookie(alice)).andExpect(jsonPath("$[0].status").value("WITHDRAWN"));

        // Asking again is allowed once the earlier request is withdrawn.
        mvc.perform(withJson(post("/api/care/review-requests"), requestBody(doctorId, "")).cookie(alice)).andExpect(status().isCreated());
        mvc.perform(delete("/api/predictions/" + aliceScan.id()).with(csrf()).cookie(alice)).andExpect(status().isNoContent());
        assertThat(mongo.count(new Query(), ReviewRequest.class)).isZero();
    }

    @Test
    void doctorsRunTheAnalysisForTheirPatient() throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of(modelPath)), "model.onnx not found; run ML-Service/export_onnx.py");
        appointment(true);
        byte[] png = new ClassPathResource("preprocessing/portrait.png").getContentAsByteArray();
        String body = mvc.perform(multipart("/api/care/my-patients/" + aliceId + "/scans").param("view", "CC")
                        .file(new MockMultipartFile("image", "mammo.png", "image/png", png)).with(csrf()).cookie(doctor))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(aliceId))
                .andExpect(jsonPath("$.createdBy").value(doctorId))
                .andExpect(jsonPath("$.createdByName").value("Doctor User"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        // It is Alice's record, in her history; the doctor can open it; she was told.
        mvc.perform(get("/api/predictions").cookie(alice)).andExpect(jsonPath("$[?(@.id == '" + id + "')]").exists());
        mvc.perform(get("/api/predictions/" + id).cookie(doctor)).andExpect(status().isOk());
        mvc.perform(get("/api/care/my-patients/" + aliceId + "/scans").cookie(doctor)).andExpect(jsonPath("$.length()").value(1));
        assertThat(mongo.count(Query.query(Criteria.where("userId").is(aliceId).and("type").is("SCAN_BY_DOCTOR")), "notifications"))
                .isEqualTo(1);
    }
}
