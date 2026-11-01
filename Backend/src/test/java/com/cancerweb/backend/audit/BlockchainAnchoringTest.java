package com.cancerweb.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.cancerweb.backend.model.Role;
import com.cancerweb.backend.support.IntegrationTest;
import com.sun.net.httpserver.HttpServer;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Anchoring against a stand-in Ethereum JSON-RPC node (the same calls Hardhat, Anvil or Ganache answer), so the test
 * needs no real network: only a proof is sent, ANCHORED needs a successful receipt, and a broken node never breaks
 * the app.
 */
class BlockchainAnchoringTest extends IntegrationTest {

    private static final String ACCOUNT = "0x" + "a1".repeat(20);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<JsonNode> SENT = new CopyOnWriteArrayList<>();
    private static final Map<String, String> TX_DATA = new ConcurrentHashMap<>();
    /** OK, REVERT (mined with status 0) or DOWN (HTTP 503). */
    private static volatile String mode = "OK";
    private static final HttpServer NODE = startNode();

    private static HttpServer startNode() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                byte[] reply;
                if ("DOWN".equals(mode)) {
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                    return;
                }
                JsonNode request = JSON.readTree(exchange.getRequestBody().readAllBytes());
                String method = request.path("method").asString();
                JsonNode params = request.path("params");
                Object result = null;
                switch (method) {
                    case "eth_sendTransaction" -> {
                        SENT.add(params.get(0));
                        String hash = "0x" + String.format("%064x", SENT.size());
                        TX_DATA.put(hash, params.get(0).path("data").asString());
                        result = hash;
                    }
                    case "eth_getTransactionReceipt" -> result = TX_DATA.containsKey(params.get(0).asString())
                            ? Map.of("status", "REVERT".equals(mode) ? "0x0" : "0x1", "blockNumber", "0x2a")
                            : null;
                    case "eth_getTransactionByHash" -> result = TX_DATA.containsKey(params.get(0).asString())
                            ? Map.of("hash", params.get(0).asString(), "input", TX_DATA.get(params.get(0).asString()))
                            : null;
                    default -> {
                    }
                }
                java.util.Map<String, Object> body = new java.util.HashMap<>();
                body.put("jsonrpc", "2.0");
                body.put("id", request.path("id").asLong());
                body.put("result", result);
                reply = JSON.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
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
        registry.add("audit.blockchain.network-name", () -> "test-node");
        registry.add("audit.blockchain.poll-interval", () -> "PT1H");
    }

    @AfterAll
    static void stopNode() {
        NODE.stop(0);
    }

    @BeforeEach
    void reset() {
        mode = "OK";
        SENT.clear();
        TX_DATA.clear();
    }

    private AuditLog loginEvent() {
        return mongo.findOne(Query.query(Criteria.where("eventType").is("USER_LOGIN")), AuditLog.class);
    }

    @Test
    void anchorsOnlyTheProofAndConfirmsItOnChain() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "admin@example.com");
        AuditLog event = loginEvent();
        assertThat(event.blockchainStatus()).isEqualTo(BlockchainStatus.PENDING);

        mvc.perform(post("/api/admin/audit-logs/" + event.id() + "/anchor").cookie(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockchainStatus").value("ANCHORED"))
                .andExpect(jsonPath("$.blockchainNetwork").value("test-node"))
                .andExpect(jsonPath("$.blockchainReference").value("0x" + String.format("%064x", 1)))
                .andExpect(jsonPath("$.hashVerified").value(true));

        assertThat(SENT).hasSize(1);
        JsonNode tx = SENT.getFirst();
        assertThat(tx.path("from").asString()).isEqualTo(ACCOUNT);
        assertThat(tx.path("to").asString()).isEqualTo(ACCOUNT);
        assertThat(tx.path("value").asString()).isEqualTo("0x0");
        String payload = new String(HexFormat.of().parseHex(tx.path("data").asString().substring(2)), StandardCharsets.UTF_8);
        JsonNode proof = JSON.readTree(payload);
        // Exactly the proof: no user, role, target or metadata.
        assertThat(proof.propertyNames()).containsExactlyInAnyOrder("app", "v", "ref", "hash", "ts");
        assertThat(proof.path("hash").asString()).isEqualTo(event.eventHash());
        assertThat(proof.path("ref").asString()).isEqualTo(event.id());
        assertThat(payload).doesNotContain("@", "admin", "USER", event.actorId());

        mvc.perform(get("/api/admin/audit-logs/" + event.id() + "/blockchain-proof").cookie(admin))
                .andExpect(jsonPath("$.matches").value(true))
                .andExpect(jsonPath("$.blockNumber").value(42))
                .andExpect(jsonPath("$.onChainPayload.hash").value(event.eventHash()));
        // Anchoring again does not send a second transaction.
        mvc.perform(post("/api/admin/audit-logs/" + event.id() + "/anchor").cookie(admin).with(csrf()))
                .andExpect(jsonPath("$.blockchainStatus").value("ANCHORED"));
        assertThat(SENT).hasSize(1);
    }

    @Test
    void aFailedTransactionOrADownNodeIsFailedAndTheAppKeepsWorking() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "admin@example.com");
        AuditLog event = loginEvent();

        mode = "REVERT";
        mvc.perform(post("/api/admin/audit-logs/" + event.id() + "/anchor").cookie(admin).with(csrf()))
                .andExpect(jsonPath("$.blockchainStatus").value("FAILED"))
                .andExpect(jsonPath("$.blockchainError").value("The transaction was mined but failed."));

        mode = "DOWN";
        mvc.perform(post("/api/admin/audit-logs/" + event.id() + "/anchor").cookie(admin).with(csrf()))
                .andExpect(jsonPath("$.blockchainStatus").value("FAILED"))
                .andExpect(jsonPath("$.blockchainError").value("The blockchain node answered HTTP 503"));
        // Logging in (and writing its audit event) still works while the node is down.
        signedIn(Role.PATIENT, "p@example.com");
        assertThat(mongo.count(Query.query(Criteria.where("eventType").is("USER_LOGIN")), AuditLog.class)).isEqualTo(2);

        mode = "OK";
        mvc.perform(post("/api/admin/audit-logs/" + event.id() + "/anchor").cookie(admin).with(csrf()))
                .andExpect(jsonPath("$.blockchainStatus").value("ANCHORED"));
    }

    @Test
    void aChangedEventIsNeverAnchored() throws Exception {
        Cookie admin = signedIn(Role.ADMIN, "admin@example.com");
        AuditLog event = loginEvent();
        mongo.updateFirst(Query.query(Criteria.where("id").is(event.id())), Update.update("targetId", "other"), AuditLog.class);
        mvc.perform(post("/api/admin/audit-logs/" + event.id() + "/anchor").cookie(admin).with(csrf()))
                .andExpect(jsonPath("$.blockchainStatus").value("FAILED"))
                .andExpect(jsonPath("$.blockchainError").value("The event failed hash verification, so it was not anchored."));
        assertThat(SENT).isEmpty();
    }

    @Test
    void anUnreachableNodeIsReportedNotHidden() {
        BlockchainService unreachable = new BlockchainService(true, "http://127.0.0.1:1", ACCOUNT, "nowhere", "", Duration.ofSeconds(2));
        AuditLog event = new AuditLog("6720c1f4a1b2c3d4e5f60718", 1, "USER_LOGIN", null, null, null, null,
                java.time.Instant.now(), Map.of(), AuditHasher.GENESIS, "ab".repeat(32), BlockchainStatus.PENDING,
                null, null, null, null);
        assertThatThrownBy(() -> unreachable.submit(event))
                .isInstanceOf(BlockchainService.BlockchainException.class)
                .hasMessage("The blockchain node could not be reached");
        // A misconfigured account keeps anchoring off instead of failing later.
        assertThat(new BlockchainService(true, "http://127.0.0.1:1", "not-an-address", "x", "", Duration.ofSeconds(1)).enabled()).isFalse();
    }
}
