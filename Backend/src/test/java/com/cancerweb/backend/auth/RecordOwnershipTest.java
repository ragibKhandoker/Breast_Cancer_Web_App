package com.cancerweb.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.repository.PredictionRepository;
import com.cancerweb.backend.repository.UserRepository;
import com.cancerweb.backend.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

/** Patients see only their own records; doctors only records shared with them; changing an id gets 404. */
class RecordOwnershipTest extends IntegrationTest {

    private static final byte[] IMAGE = { 9, 8, 7, 6 };

    @Autowired
    private PredictionRepository predictions;

    @Autowired
    private UserRepository users;

    @Autowired
    private GridFsTemplate gridFs;

    private Cookie alice;
    private Cookie bob;
    private Cookie doctor;
    private Cookie otherDoctor;
    private String aliceId;
    private String doctorId;
    private Prediction aliceScan;

    @BeforeEach
    void setUp() throws Exception {
        alice = signedIn(Role.PATIENT, "alice@example.com");
        bob = signedIn(Role.PATIENT, "bob@example.com");
        doctor = signedIn(Role.DOCTOR, "doctor@example.com");
        otherDoctor = signedIn(Role.DOCTOR, "other.doctor@example.com");
        aliceId = users.findByEmail("alice@example.com").orElseThrow().id();
        doctorId = users.findByEmail("doctor@example.com").orElseThrow().id();
        aliceScan = store(aliceId);
        store(users.findByEmail("bob@example.com").orElseThrow().id());
    }

    private Prediction store(String ownerId) {
        ObjectId imageId = gridFs.store(new ByteArrayInputStream(IMAGE), "scan.png", "image/png");
        return predictions.save(new Prediction(null, "scan.png", "image/png", IMAGE.length, imageId.toHexString(),
                "benign", 0.2, 0.8, "test", false, Instant.now(), ownerId, List.of(), null, "MLO", 0.5, false, null, null));
    }

    private String path(String suffix) {
        return "/api/predictions/" + aliceScan.id() + suffix;
    }

    @Test
    void patientListsOnlyOwnRecords() throws Exception {
        mvc.perform(get("/api/predictions").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(aliceScan.id()))
                .andExpect(jsonPath("$[0].ownerId").value(aliceId))
                .andExpect(jsonPath("$[0].sharedWithDoctorIds").doesNotExist());
    }

    @Test
    void ownerOpensOwnReportAndImage() throws Exception {
        mvc.perform(get(path("")).cookie(alice)).andExpect(status().isOk()).andExpect(jsonPath("$.label").value("benign"));
        mvc.perform(get(path("/image")).cookie(alice))
                .andExpect(status().isOk())
                .andExpect(content().bytes(IMAGE));
    }

    @Test
    void anotherPatientCannotReadImageDeleteOrShareTheRecord() throws Exception {
        mvc.perform(get(path("")).cookie(bob)).andExpect(status().isNotFound());
        mvc.perform(get(path("/image")).cookie(bob)).andExpect(status().isNotFound());
        mvc.perform(get(path("/heatmap")).cookie(bob)).andExpect(status().isNotFound());
        mvc.perform(get(path("/shares")).cookie(bob)).andExpect(status().isNotFound());
        mvc.perform(delete(path("")).cookie(bob).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(withJson(post(path("/shares")), "{\"doctorId\":\"" + doctorId + "\"}").cookie(bob))
                .andExpect(status().isNotFound());
        assertThat(predictions.findById(aliceScan.id())).isPresent();
        assertThat(predictions.findById(aliceScan.id()).orElseThrow().sharedWithDoctorIds()).isEmpty();
    }

    @Test
    void doctorSeesOnlyReportsSharedWithThem() throws Exception {
        mvc.perform(get(path("")).cookie(doctor)).andExpect(status().isNotFound());
        mvc.perform(get("/api/predictions/shared").cookie(doctor)).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(withJson(post(path("/shares")), "{\"doctorId\":\"" + doctorId + "\"}").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(doctorId));

        mvc.perform(get(path("")).cookie(doctor)).andExpect(status().isOk());
        mvc.perform(get(path("/image")).cookie(doctor)).andExpect(status().isOk()).andExpect(content().bytes(IMAGE));
        mvc.perform(get("/api/predictions/shared").cookie(doctor))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].report.id").value(aliceScan.id()))
                .andExpect(jsonPath("$[0].patient.fullName").value("Patient User"))
                .andExpect(jsonPath("$[0].patient.email").doesNotExist());

        // Sharing with one doctor does not open it to others, and doctors cannot delete.
        mvc.perform(get(path("")).cookie(otherDoctor)).andExpect(status().isNotFound());
        mvc.perform(get(path("/heatmap")).cookie(otherDoctor)).andExpect(status().isNotFound());
        mvc.perform(delete(path("")).cookie(doctor).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void ownerDownloadsTheScanReportAndOthersCannot() throws Exception {
        String report = "/api/scans/" + aliceScan.id() + "/report";
        byte[] pdf = mvc.perform(get(report).cookie(alice))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"breastcare-report-" + aliceScan.id() + ".pdf\""))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();
        String text;
        try (var doc = Loader.loadPDF(pdf)) {
            text = new PDFTextStripper().getText(doc);
        }
        assertThat(text).contains(aliceScan.id(), "scan.png", "MLO (mediolateral oblique)", "Patient User",
                "Benign", "20.0%", "80.0%", "Low risk", "test", "Not a medical diagnosis",
                "The scan image could not be read.");

        // Stored: the next download is the same document.
        String reportFileId = predictions.findById(aliceScan.id()).orElseThrow().reportFileId();
        assertThat(reportFileId).isNotNull();
        mvc.perform(get(report).cookie(alice)).andExpect(content().bytes(pdf));

        mvc.perform(get(report).cookie(bob)).andExpect(status().isNotFound());
        mvc.perform(get(report).cookie(doctor)).andExpect(status().isNotFound());
        mvc.perform(get("/api/scans/000000000000000000000000/report").cookie(alice)).andExpect(status().isNotFound());
        mvc.perform(get("/api/scans/{id}/report", "{\"$ne\":null}").cookie(alice)).andExpect(status().isNotFound());

        // A doctor the scan is shared with gets the same report; another doctor still does not.
        mvc.perform(withJson(post(path("/shares")), "{\"doctorId\":\"" + doctorId + "\"}").cookie(alice));
        mvc.perform(get(report).cookie(doctor)).andExpect(status().isOk()).andExpect(content().bytes(pdf));
        mvc.perform(get(report).cookie(otherDoctor)).andExpect(status().isNotFound());
        // Administrators manage accounts but do not read medical records.
        mvc.perform(get(report).cookie(signedIn(Role.ADMIN, "admin@example.com"))).andExpect(status().isForbidden());

        // Deleting the scan deletes its report too.
        mvc.perform(delete(path("")).cookie(alice).with(csrf())).andExpect(status().isNoContent());
        assertThat(gridFs.findOne(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("_id").is(new ObjectId(reportFileId)))))
                .isNull();
        mvc.perform(get(report).cookie(alice)).andExpect(status().isNotFound());
    }

    @Test
    void unsharingRemovesTheDoctorsAccess() throws Exception {
        mvc.perform(withJson(post(path("/shares")), "{\"doctorId\":\"" + doctorId + "\"}").cookie(alice));
        mvc.perform(delete(path("/shares/" + doctorId)).cookie(alice).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(path("")).cookie(doctor)).andExpect(status().isNotFound());
        mvc.perform(get(path("/shares")).cookie(alice)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void reportsCanOnlyBeSharedWithDoctors() throws Exception {
        String bobId = users.findByEmail("bob@example.com").orElseThrow().id();
        mvc.perform(withJson(post(path("/shares")), "{\"doctorId\":\"" + bobId + "\"}").cookie(alice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Doctor not found"));
        mvc.perform(withJson(post(path("/shares")), "{\"doctorId\":\"\"}").cookie(alice))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patientsSeeTheDoctorDirectoryWithNamesOnly() throws Exception {
        mvc.perform(get("/api/doctors").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fullName").exists())
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    void ownerDeletesRecordAndImage() throws Exception {
        mvc.perform(delete(path("")).cookie(alice).with(csrf())).andExpect(status().isNoContent());
        assertThat(predictions.findById(aliceScan.id())).isEmpty();
        assertThat(gridFs.findOne(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("_id").is(new ObjectId(aliceScan.imageFileId())))))
                .isNull();
    }

    @Test
    void recordsFromBeforeAccountsAreKeptButHidden() throws Exception {
        Prediction legacy = store(null);
        mvc.perform(get("/api/predictions/" + legacy.id()).cookie(alice)).andExpect(status().isNotFound());
        mvc.perform(get("/api/predictions").cookie(alice)).andExpect(jsonPath("$.length()").value(1));
        assertThat(predictions.countByOwnerIdIsNull()).isEqualTo(1);
    }

    @Test
    void unknownAndMalformedIdsAre404() throws Exception {
        mvc.perform(get("/api/predictions/000000000000000000000000").cookie(alice)).andExpect(status().isNotFound());
        mvc.perform(get("/api/predictions/not-an-id").cookie(alice)).andExpect(status().isNotFound());
        mvc.perform(get("/api/predictions/{id}", "{\"$ne\":null}").cookie(alice)).andExpect(status().isNotFound());
    }
}
