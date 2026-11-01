package com.cancerweb.backend.audit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.repository.PredictionRepository;
import com.cancerweb.backend.support.IntegrationTest;

import jakarta.servlet.http.Cookie;

/** Without a blockchain the off-chain record is still checked, and says so; older reports are reported honestly. */
class ReportIntegrityOffChainTest extends IntegrationTest {

    @Autowired
    private PredictionRepository predictions;

    @Autowired
    private GridFsTemplate gridFs;

    @Test
    void checksTheOffChainRecordAndExplainsWhatWasNotChecked() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        String owner = userService.listAll().getFirst().id();
        ObjectId image = gridFs.store(new ByteArrayInputStream(new byte[] { 1, 2, 3 }), "scan.png", "image/png");
        Prediction scan = predictions.save(new Prediction(null, "scan.png", "image/png", 3, image.toHexString(), "benign",
                0.1, 0.9, "v1", false, Instant.now(), owner, List.of(), null, "CC", 0.5, false, null, null));
        mvc.perform(get("/api/scans/" + scan.id() + "/report").cookie(patient));
        mvc.perform(post("/api/scans/" + scan.id() + "/integrity/verify").cookie(patient).with(csrf()))
                .andExpect(jsonPath("$.status").value("OFF_CHAIN_VERIFIED"))
                .andExpect(jsonPath("$.reportMatches").value(true))
                .andExpect(jsonPath("$.recordIntact").value(true))
                .andExpect(jsonPath("$.blockchainStatus").value("NOT_CONFIGURED"));

        // A report made before integrity records existed.
        ObjectId oldPdf = gridFs.store(new ByteArrayInputStream("%PDF old".getBytes()), "old.pdf", "application/pdf");
        Prediction older = predictions.save(new Prediction(null, "scan.png", "image/png", 3, image.toHexString(), "benign",
                0.1, 0.9, "v1", false, Instant.now(), owner, List.of(), null, "CC", 0.5, false, null, oldPdf.toHexString()));
        mvc.perform(get("/api/scans/" + older.id() + "/integrity").cookie(patient))
                .andExpect(jsonPath("$.status").value("NOT_RECORDED"));
    }
}
