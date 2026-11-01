package com.cancerweb.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.repository.PredictionRepository;
import com.cancerweb.backend.support.IntegrationTest;
import com.sun.net.httpserver.HttpServer;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Report integrity end to end against a stand-in for the AuditIntegrity contract (blockchain/contracts): it decodes
 * the ABI calls the backend sends and keeps write-once records like the Solidity contract does.
 */
class ReportIntegrityTest extends IntegrationTest {

    private static final String ACCOUNT = "0x" + "b2".repeat(20);
    private static final String CONTRACT = "0x" + "c3".repeat(20);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HexFormat HEX = HexFormat.of();
    /** ref (64 hex) -> [hash, timestamp] as stored by register(). */
    private static final Map<String, String[]> RECORDS = new ConcurrentHashMap<>();
    private static final List<String> REGISTER_CALLS = new CopyOnWriteArrayList<>();
    private static final Map<String, Boolean> TXS = new ConcurrentHashMap<>();
    private static final HttpServer NODE = startContractNode();

    private static String word(long v) {
        return String.format("%064x", v);
    }

    private static HttpServer startContractNode() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            String register = HEX.formatHex(BlockchainService.REGISTER);
            String verify = HEX.formatHex(BlockchainService.VERIFY);
            String getRecord = HEX.formatHex(BlockchainService.GET_RECORD);
            server.createContext("/", exchange -> {
                JsonNode request = JSON.readTree(exchange.getRequestBody().readAllBytes());
                JsonNode params = request.path("params");
                Map<String, Object> body = new HashMap<>();
                body.put("jsonrpc", "2.0");
                body.put("id", request.path("id").asLong());
                switch (request.path("method").asString()) {
                    case "eth_sendTransaction" -> {
                        JsonNode tx = params.get(0);
                        String data = tx.path("data").asString().substring(2);
                        if (!CONTRACT.equals(tx.path("to").asString()) || !data.startsWith(register) || data.length() != 8 + 3 * 64) {
                            body.put("error", Map.of("code", -32000, "message", "unexpected transaction"));
                            break;
                        }
                        REGISTER_CALLS.add(data);
                        String ref = data.substring(8, 72);
                        String hash = data.substring(72, 136);
                        String[] existing = RECORDS.get(ref);
                        if (existing != null && !existing[0].equals(hash)) {
                            body.put("error", Map.of("code", 3, "message", "execution reverted: ConflictingHash"));
                            break;
                        }
                        RECORDS.putIfAbsent(ref, new String[] { hash, data.substring(136) });
                        String txHash = "0x" + word(REGISTER_CALLS.size());
                        TXS.put(txHash, true);
                        body.put("result", txHash);
                    }
                    case "eth_getTransactionReceipt" -> body.put("result",
                            TXS.containsKey(params.get(0).asString()) ? Map.of("status", "0x1", "blockNumber", "0x7") : null);
                    case "eth_call" -> {
                        String data = params.get(0).path("data").asString().substring(2);
                        String ref = data.substring(8, 72);
                        String[] r = RECORDS.get(ref);
                        if (data.startsWith(getRecord)) {
                            body.put("result", r == null ? "0x" + "0".repeat(192)
                                    : "0x" + r[0] + r[1] + "000000000000000000000000" + ACCOUNT.substring(2));
                        } else if (data.startsWith(verify)) {
                            boolean ok = r != null && r[0].equals(data.substring(72, 136));
                            body.put("result", "0x" + word(ok ? 1 : 0));
                        }
                    }
                    default -> body.put("result", null);
                }
                byte[] reply = JSON.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, reply.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(reply);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void blockchain(DynamicPropertyRegistry registry) {
        registry.add("audit.blockchain.enabled", () -> "true");
        registry.add("audit.blockchain.rpc-url", () -> "http://127.0.0.1:" + NODE.getAddress().getPort());
        registry.add("audit.blockchain.account", () -> ACCOUNT);
        registry.add("audit.blockchain.contract", () -> CONTRACT);
        registry.add("audit.blockchain.network-name", () -> "hardhat-test");
        registry.add("audit.blockchain.poll-interval", () -> "PT1H");
    }

    @AfterAll
    static void stopNode() {
        NODE.stop(0);
    }

    @BeforeEach
    void reset() {
        RECORDS.clear();
        REGISTER_CALLS.clear();
        TXS.clear();
    }

    @Autowired
    private PredictionRepository predictions;

    @Autowired
    private GridFsTemplate gridFs;

    private String idOf(String email) {
        return userService.listAll().stream().filter(u -> u.email().equals(email)).findFirst().orElseThrow().id();
    }

    private Prediction scanOf(String ownerId) {
        ObjectId image = gridFs.store(new ByteArrayInputStream(new byte[] { 1, 2, 3 }), "scan.png", "image/png");
        return predictions.save(new Prediction(null, "scan.png", "image/png", 3, image.toHexString(), "benign", 0.1, 0.9,
                "v1", false, Instant.now(), ownerId, List.of(), null, "CC", 0.5, false, null, null));
    }

    @Test
    void aReportIsRecordedOffChainAnchoredInTheContractAndVerified() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        Prediction scan = scanOf(idOf("p@example.com"));
        String integrity = "/api/scans/" + scan.id() + "/integrity";

        mvc.perform(get(integrity).cookie(patient)).andExpect(jsonPath("$.status").value("NO_REPORT"));
        byte[] pdf = mvc.perform(get("/api/scans/" + scan.id() + "/report").cookie(patient))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        // Off-chain: the full record, with the PDF's SHA-256 computed by the backend.
        AuditLog record = mongo.findOne(Query.query(Criteria.where("eventType").is("REPORT_GENERATED")), AuditLog.class);
        assertThat(record.targetId()).isEqualTo(scan.id());
        assertThat(record.metadata()).containsEntry("reportSha256", AuditHasher.sha256Bytes(pdf));
        mvc.perform(get(integrity).cookie(patient))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value("Blockchain verification is currently pending."));

        mvc.perform(post(integrity + "/verify").cookie(patient).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.message").value("This report matches its original recorded integrity hash."))
                .andExpect(jsonPath("$.onChainMatches").value(true))
                .andExpect(jsonPath("$.blockchainStatus").value("ANCHORED"))
                .andExpect(jsonPath("$.network").value("hardhat-test"))
                .andExpect(jsonPath("$.transactionReference").isString());

        // On-chain: exactly register(SHA-256(event id), event hash, hour) and nothing else.
        assertThat(REGISTER_CALLS).hasSize(1);
        String call = REGISTER_CALLS.getFirst();
        AuditLog stored = mongo.findById(record.id(), AuditLog.class);
        assertThat(call.substring(8, 72)).isEqualTo(AuditHasher.sha256(record.id()));
        assertThat(call.substring(72, 136)).isEqualTo(stored.eventHash());
        assertThat(Long.parseLong(call.substring(136), 16) % 3600).isZero();
        assertThat(call).doesNotContain(scan.id(), idOf("p@example.com"), AuditHasher.sha256Bytes(pdf));

        // A quick status now reads VERIFIED from the stored anchoring, without calling the chain again.
        mvc.perform(get(integrity).cookie(patient)).andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    @Test
    void aChangedReportOrADifferentOnChainProofFails() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        Prediction scan = scanOf(idOf("p@example.com"));
        String integrity = "/api/scans/" + scan.id() + "/integrity";
        mvc.perform(get("/api/scans/" + scan.id() + "/report").cookie(patient)).andExpect(status().isOk());
        mvc.perform(post(integrity + "/verify").cookie(patient).with(csrf())).andExpect(jsonPath("$.status").value("VERIFIED"));

        // The chain now holds a different hash for this record.
        RECORDS.values().forEach(r -> r[0] = "ee".repeat(32));
        mvc.perform(post(integrity + "/verify").cookie(patient).with(csrf()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.onChainMatches").value(false))
                .andExpect(jsonPath("$.message").value("The report content does not match the originally recorded integrity hash."));
        reset();

        // Someone swaps the stored PDF.
        String fake = gridFs.store(new ByteArrayInputStream("%PDF-1.4 changed".getBytes(StandardCharsets.US_ASCII)),
                "report.pdf", "application/pdf").toHexString();
        mongo.updateFirst(Query.query(Criteria.where("id").is(scan.id())), Update.update("reportFileId", fake), Prediction.class);
        mvc.perform(post(integrity + "/verify").cookie(patient).with(csrf()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.reportMatches").value(false));
        assertThat(mongo.count(Query.query(Criteria.where("eventType").is("REPORT_VERIFICATION_FAILED")), AuditLog.class)).isEqualTo(2);
    }

    @Test
    void onlyThePatientAndDoctorsTheyShareWithCanCheckIntegrity() throws Exception {
        Cookie patient = signedIn(Role.PATIENT, "p@example.com");
        Cookie other = signedIn(Role.PATIENT, "other@example.com");
        Cookie doctor = signedIn(Role.DOCTOR, "d@example.com");
        Cookie admin = signedIn(Role.ADMIN, "admin@example.com");
        Prediction scan = scanOf(idOf("p@example.com"));
        String integrity = "/api/scans/" + scan.id() + "/integrity";
        mvc.perform(get("/api/scans/" + scan.id() + "/report").cookie(patient)).andExpect(status().isOk());

        mvc.perform(get(integrity)).andExpect(status().isUnauthorized());
        mvc.perform(get(integrity).cookie(other)).andExpect(status().isNotFound());
        mvc.perform(post(integrity + "/verify").cookie(other).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get(integrity).cookie(doctor)).andExpect(status().isNotFound());
        mvc.perform(get(integrity).cookie(admin)).andExpect(status().isForbidden());

        mongo.updateFirst(Query.query(Criteria.where("id").is(scan.id())),
                new Update().addToSet("sharedWithDoctorIds", idOf("d@example.com")), Prediction.class);
        mvc.perform(post(integrity + "/verify").cookie(doctor).with(csrf())).andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    @Test
    void healthProbesAreOpenForKubernetes() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }
}
