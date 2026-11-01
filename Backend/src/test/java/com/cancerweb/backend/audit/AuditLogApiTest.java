package com.cancerweb.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.repository.PredictionRepository;
import com.cancerweb.backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/** Events are written by the backend itself, only administrators can read them, and tampering is detected. */
class AuditLogApiTest extends IntegrationTest {

    private static final String API = "/api/admin/audit-logs";

    @Autowired
    private PredictionRepository predictions;

    @Autowired
    private GridFsTemplate gridFs;

    private List<AuditLog> events(AuditEventType type) {
        return mongo.find(Query.query(Criteria.where("eventType").is(type.name())).with(Sort.by("sequence")), AuditLog.class);
    }

    private String idOf(String email) {
        return userService.listAll().stream().filter(u -> u.email().equals(email)).findFirst().orElseThrow().id();
    }

    @Test
    void loginFailedLoginAndLogoutAreRecordedWithoutTheEmail() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        String id = idOf("p@example.com");
        mvc.perform(withJson(post("/api/auth/login"), "{\"email\":\"p@example.com\",\"password\":\"Wrong12345\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(withJson(post("/api/auth/login"), "{\"email\":\"nobody@example.com\",\"password\":\"Wrong12345\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").cookie(patient).with(csrf())).andExpect(status().isNoContent());

        assertThat(events(AuditEventType.USER_LOGIN)).singleElement()
                .satisfies(e -> assertThat(e.actorId()).isEqualTo(id));
        // Only the existing account's failed attempt, by id; the unknown email leaves nothing.
        assertThat(events(AuditEventType.USER_LOGIN_FAILED)).singleElement()
                .satisfies(e -> {
                    assertThat(e.targetId()).isEqualTo(id);
                    assertThat(e.actorId()).isNull();
                });
        assertThat(events(AuditEventType.USER_LOGOUT)).singleElement()
                .satisfies(e -> assertThat(e.actorId()).isEqualTo(id));
    }

    @Test
    void roleChangesReviewsAndReportDownloadsAreRecordedByTheBackend() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "admin@example.com");
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        String adminId = idOf("admin@example.com");
        String patientId = idOf("p@example.com");

        mvc.perform(withJson(patch("/api/admin/users/" + patientId + "/role"), "{\"role\":\"DOCTOR\"}").cookie(admin))
                .andExpect(status().isOk());
        assertThat(events(AuditEventType.ADMIN_ROLE_CHANGED)).singleElement().satisfies(e -> {
            assertThat(e.actorId()).isEqualTo(adminId);
            assertThat(e.targetId()).isEqualTo(patientId);
            assertThat(e.metadata()).containsEntry("previousRole", "PATIENT").containsEntry("newRole", "DOCTOR");
        });
        mvc.perform(withJson(patch("/api/admin/users/" + patientId + "/role"), "{\"role\":\"PATIENT\"}").cookie(admin));

        mvc.perform(withJson(patch("/api/admin/users/" + patientId + "/verification"),
                        "{\"status\":\"REJECTED\",\"note\":\"Please add your date of birth\"}").cookie(admin))
                .andExpect(status().isOk());
        assertThat(events(AuditEventType.REVIEW_UPDATED)).singleElement().satisfies(e -> {
            assertThat(e.actorId()).isEqualTo(adminId);
            assertThat(e.metadata()).containsEntry("reviewType", "ACCOUNT_VERIFICATION").containsEntry("status", "REJECTED");
            // The reviewer's note is personal and stays out of the log.
            assertThat(e.metadata().values()).noneMatch(v -> v.contains("birth"));
        });

        ObjectId imageId = gridFs.store(new ByteArrayInputStream(new byte[] { 1, 2, 3 }), "patient-name.png", "image/png");
        Prediction scan = predictions.save(new Prediction(null, "patient-name.png", "image/png", 3, imageId.toHexString(),
                "malignant", 0.9, 0.1, "v1", false, Instant.now(), patientId, List.of(), null, "CC", 0.5, false, null, null));
        mvc.perform(get("/api/scans/" + scan.id() + "/report").cookie(patient)).andExpect(status().isOk());
        assertThat(events(AuditEventType.REPORT_DOWNLOADED)).singleElement().satisfies(e -> {
            assertThat(e.actorId()).isEqualTo(patientId);
            assertThat(e.targetType()).isEqualTo("SCAN");
            assertThat(e.targetId()).isEqualTo(scan.id());
        });

        // Nothing personal or medical anywhere in the log: no names, emails, file names or results.
        for (Document d : mongo.getCollection("audit_logs").find()) {
            String raw = d.toJson();
            assertThat(raw).doesNotContain("@example.com", "Patient User", "Admin User", "patient-name", "malignant",
                    "Secret123", "date of birth");
        }
    }

    @Test
    void onlyAdministratorsCanReadTheLogAndNobodyCanChangeIt() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        Cookie doctor = signedIn(Role.DOCTOR, "d@example.com");
        Cookie admin = signedIn(Role.ADMIN, "admin@example.com");
        String id = events(AuditEventType.USER_LOGIN).getFirst().id();

        for (String path : new String[] { API, API + "/stats", API + "/" + id, API + "/" + id + "/verify", API + "/verify-chain" }) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).cookie(patient)).andExpect(status().isForbidden());
            mvc.perform(get(path).cookie(doctor)).andExpect(status().isForbidden());
            mvc.perform(get(path).cookie(admin)).andExpect(status().isOk());
        }
        mvc.perform(post(API + "/" + id + "/anchor").cookie(patient).with(csrf())).andExpect(status().isForbidden());
        // There is no endpoint that edits or deletes events.
        mvc.perform(delete(API + "/" + id).cookie(admin).with(csrf())).andExpect(status().is4xxClientError());
        mvc.perform(withJson(patch(API + "/" + id), "{\"eventType\":\"USER_LOGOUT\"}").cookie(admin))
                .andExpect(status().is4xxClientError());
        assertThat(mongo.findById(id, AuditLog.class).eventType()).isEqualTo("USER_LOGIN");
    }

    @Test
    void listsFiltersAndPagesEvents() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "admin@example.com");
        signedIn(Role.PATIENT, "p1@example.com");
        signedIn(Role.PATIENT, "p2@example.com");
        String p1 = idOf("p1@example.com");

        mvc.perform(get(API).param("size", "2").cookie(admin))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].sequence").value(3))
                .andExpect(jsonPath("$.items[0].hashVerified").value(true))
                .andExpect(jsonPath("$.items[0].blockchainStatus").value("NOT_CONFIGURED"));
        mvc.perform(get(API).param("size", "2").param("page", "1").cookie(admin))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sequence").value(1));
        mvc.perform(get(API).param("actorId", p1).cookie(admin))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].actorId").value(p1));
        mvc.perform(get(API).param("eventType", "USER_LOGIN").param("from", "2000-01-01").param("to", "2999-12-31")
                        .param("blockchainStatus", "NOT_CONFIGURED").cookie(admin))
                .andExpect(jsonPath("$.total").value(3));
        mvc.perform(get(API).param("from", "2999-01-01").cookie(admin)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get(API).param("eventType", "NOT_A_TYPE").cookie(admin)).andExpect(status().isBadRequest());
        mvc.perform(get(API).param("blockchainStatus", "MAYBE").cookie(admin)).andExpect(status().isBadRequest());
        mvc.perform(get(API).param("actorId", "{\"$ne\":null}").cookie(admin)).andExpect(status().isBadRequest());
        mvc.perform(get(API + "/stats").cookie(admin))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.byType.USER_LOGIN").value(3))
                .andExpect(jsonPath("$.blockchain.enabled").value(false));
        mvc.perform(get(API + "/not-an-id").cookie(admin)).andExpect(status().isNotFound());
    }

    @Test
    void verificationDetectsChangedAndMissingEvents() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "admin@example.com");
        signedIn(Role.PATIENT, "p1@example.com");
        signedIn(Role.PATIENT, "p2@example.com");
        List<AuditLog> chain = mongo.find(new Query().with(Sort.by("sequence")), AuditLog.class);
        assertThat(chain).hasSize(3);
        assertThat(chain.get(0).previousHash()).isEqualTo(AuditHasher.GENESIS);
        assertThat(chain.get(1).previousHash()).isEqualTo(chain.get(0).eventHash());

        String second = chain.get(1).id();
        mvc.perform(get(API + "/" + second + "/verify").cookie(admin))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.chainStatus").value("LINKED"));
        mvc.perform(get(API + "/verify-chain").cookie(admin))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.checked").value(3));

        // Someone edits the database directly: the event no longer matches its hash.
        mongo.updateFirst(Query.query(Criteria.where("id").is(second)), Update.update("actorId", "someone-else"), AuditLog.class);
        mvc.perform(get(API + "/" + second + "/verify").cookie(admin))
                .andExpect(jsonPath("$.status").value("TAMPERED"));
        mvc.perform(get(API + "/" + second).cookie(admin))
                .andExpect(jsonPath("$.event.hashVerified").value(false))
                .andExpect(jsonPath("$.verification.status").value("TAMPERED"));
        mvc.perform(get(API + "/verify-chain").cookie(admin))
                .andExpect(jsonPath("$.status").value("BROKEN"))
                .andExpect(jsonPath("$.firstBadSequence").value(2));

        // Deleting an event breaks the link from the next one.
        mongo.remove(Query.query(Criteria.where("id").is(second)), AuditLog.class);
        String third = chain.get(2).id();
        mvc.perform(get(API + "/" + third + "/verify").cookie(admin))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.chainStatus").value("BROKEN"));
        String body = mvc.perform(get(API + "/verify-chain").cookie(admin)).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.problem")).contains("missing");
    }

    @Test
    void anchoringIsRefusedWhenNoBlockchainIsConfigured() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "admin@example.com");
        String id = events(AuditEventType.USER_LOGIN).getFirst().id();
        mvc.perform(post(API + "/" + id + "/anchor").cookie(admin).with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Blockchain anchoring is not configured on this server."));
        mvc.perform(get(API + "/" + id + "/blockchain-proof").cookie(admin)).andExpect(status().isServiceUnavailable());
        assertThat(mongo.findById(id, AuditLog.class).blockchainStatus()).isEqualTo(BlockchainStatus.NOT_CONFIGURED);
    }
}
