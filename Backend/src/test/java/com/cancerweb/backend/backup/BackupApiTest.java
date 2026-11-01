package com.cancerweb.backend.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import com.cancerweb.backend.audit.AuditLog;
import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.repository.PredictionRepository;
import com.cancerweb.backend.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import com.mongodb.client.MongoClient;

import jakarta.servlet.http.Cookie;

class BackupApiTest extends IntegrationTest {

    private static final String API = "/api/admin/backups";

    @Autowired
    private PredictionRepository predictions;

    @Autowired
    private GridFsTemplate gridFs;

    @Autowired
    private MongoClient client;

    @BeforeEach
    void emptyBackupFolder() throws IOException {
        try (Stream<Path> files = Files.list(BACKUP_DIR)) {
            for (Path p : files.toList()) {
                Files.delete(p);
            }
        }
    }

    private Cookie dataAndAdmin() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "admin@example.com");
        signedIn(Role.PATIENT, "patient.secret@example.com");
        String owner = userService.listAll().stream().filter(u -> u.email().startsWith("patient.secret")).findFirst().orElseThrow().id();
        var image = gridFs.store(new ByteArrayInputStream("mammogram pixels".getBytes(StandardCharsets.UTF_8)), "scan.png", "image/png");
        predictions.save(new Prediction(null, "scan.png", "image/png", 16, image.toHexString(), "benign", 0.1, 0.9, "v1",
                false, Instant.now(), owner, List.of(), null, "CC", 0.5, false, null, null));
        return admin;
    }

    private String createBackup(Cookie admin) throws Exception {
        String body = mvc.perform(post(API).cookie(admin).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file").value(org.hamcrest.Matchers.matchesPattern("backup_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}\\.bcbk")))
                .andExpect(jsonPath("$.sha256").isString())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.file");
    }

    @Test
    void createsAnEncryptedBackupAndRecordsIt() throws Exception {
        Cookie admin = dataAndAdmin();
        String file = createBackup(admin);

        Path backup = BACKUP_DIR.resolve(file);
        byte[] bytes = Files.readAllBytes(backup);
        assertThat(new String(bytes, 0, 8, StandardCharsets.US_ASCII)).isEqualTo("BCBACKUP");
        String raw = new String(bytes, StandardCharsets.ISO_8859_1);
        // Encrypted: no email, name or password hash is readable in the file.
        assertThat(raw).doesNotContain("patient.secret", "Patient User", "$2a$", "users", "mammogram pixels");
        String meta = Files.readString(BACKUP_DIR.resolve(file.replace(".bcbk", ".meta.json")));
        assertThat(meta).contains("\"users\":", "\"predictions\":1", "\"fs.files\":1").doesNotContain("@example.com");

        mvc.perform(get(API).cookie(admin))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].file").value(file))
                .andExpect(jsonPath("$[0].collections.users").value(2));
        mvc.perform(get(API + "/status").cookie(admin))
                .andExpect(jsonPath("$.encryptionConfigured").value(true))
                .andExpect(jsonPath("$.lastBackup.file").value(file))
                .andExpect(jsonPath("$.restoreEnabled").value(true));
        mvc.perform(post(API + "/" + file + "/verify").cookie(admin).with(csrf())).andExpect(status().isOk());
        assertThat(mongo.find(Query.query(Criteria.where("eventType").is("BACKUP_CREATED")), AuditLog.class))
                .singleElement().satisfies(e -> assertThat(e.targetId()).isEqualTo(file));
    }

    @Test
    void aChangedBackupFailsItsIntegrityCheckAndIsNotRestored() throws Exception {
        Cookie admin = dataAndAdmin();
        String file = createBackup(admin);
        Path backup = BACKUP_DIR.resolve(file);
        byte[] bytes = Files.readAllBytes(backup);
        bytes[bytes.length / 2] ^= 0x01;
        Files.write(backup, bytes);

        mvc.perform(post(API + "/" + file + "/verify").cookie(admin).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("integrity check")));
        mvc.perform(withJson(post(API + "/restore-test"), "{\"file\":\"" + file + "\"}").cookie(admin))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void restoresIntoASeparateTestDatabaseAndChecksTheRecords() throws Exception {
        Cookie admin = dataAndAdmin();
        long users = mongo.getCollection("users").countDocuments();
        long events = mongo.getCollection("audit_logs").countDocuments();
        String file = createBackup(admin);

        mvc.perform(withJson(post(API + "/restore-test"), "{\"file\":\"" + file + "\"}").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.targetDatabase").value(TEST_DATABASE + "_restore_test"))
                .andExpect(jsonPath("$.restored.users").value(users))
                .andExpect(jsonPath("$.restored.predictions").value(1))
                .andExpect(jsonPath("$.restored.audit_logs").value(events)) // BACKUP_CREATED is written after the file
                .andExpect(jsonPath("$.restored['fs.chunks']").value(1))
                .andExpect(jsonPath("$.auditEventsTampered").value(0))
                .andExpect(jsonPath("$.problems.length()").value(0));
        // The test copy is dropped afterwards, and the live database is untouched.
        assertThat(client.listDatabaseNames().into(new java.util.ArrayList<>())).doesNotContain(TEST_DATABASE + "_restore_test");
        assertThat(mongo.getCollection("users").countDocuments()).isEqualTo(users);
        assertThat(mongo.find(Query.query(Criteria.where("eventType").is("BACKUP_RESTORE_TESTED")), AuditLog.class))
                .singleElement().satisfies(e -> assertThat(e.metadata()).containsEntry("outcome", "PASSED"));
        mvc.perform(get(API + "/status").cookie(admin)).andExpect(jsonPath("$.lastRestoreTest.outcome").value("PASSED"));
    }

    @Test
    void neverRestoresIntoTheLiveDatabaseAndOnlyAdminsGetIn() throws Exception {
        Cookie admin = dataAndAdmin();
        Cookie patient = login("patient.secret@example.com", PASSWORD);
        String file = createBackup(admin);
        mvc.perform(withJson(post(API + "/restore-test"), "{\"file\":\"" + file + "\",\"targetDatabase\":\"" + TEST_DATABASE + "\"}").cookie(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(withJson(post(API + "/restore-test"), "{\"file\":\"" + file + "\",\"targetDatabase\":\"other\"}").cookie(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(withJson(post(API + "/restore-test"), "{\"file\":\"../../etc/passwd\"}").cookie(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(post(API + "/backup_2020-01-01_00-00-00.bcbk/verify").cookie(admin).with(csrf())).andExpect(status().isNotFound());

        mvc.perform(get(API).cookie(patient)).andExpect(status().isForbidden());
        mvc.perform(post(API).cookie(patient).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(withJson(post(API + "/restore-test"), "{\"file\":\"" + file + "\"}").cookie(patient)).andExpect(status().isForbidden());
        mvc.perform(get(API + "/status")).andExpect(status().isUnauthorized());
    }
}
