package com.cancerweb.backend.audit;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Anchors audit-event proofs on a permissioned or private test Ethereum network (Hardhat, Anvil, Ganache, or a
 * private Geth/Besu chain) over JSON-RPC. Only a proof goes on chain: the event's SHA-256 hash, its timestamp and its
 * opaque event id, as UTF-8 JSON in the data field of a zero-value transaction from the configured account to
 * itself. Nothing about patients, scans or results is ever sent.
 *
 * <p>The node signs with its own unlocked development account ({@code eth_sendTransaction}), so this application
 * holds no private key. An event counts as anchored only when the transaction's receipt is mined with status 1.
 *
 * <p>With {@code audit.blockchain.contract} set (the AuditIntegrity contract from /blockchain, deployed with Hardhat),
 * proofs are registered in the contract instead: {@code register(ref, hash, timestamp)} where ref is SHA-256 of the
 * event id (opaque), hash is the event's SHA-256 and timestamp is the event time rounded down to the hour. Proofs
 * are then read back with {@code getRecord} and {@code verify} ({@code eth_call}).
 *
 * <p>Off by default ({@code audit.blockchain.enabled=false}): events are then NOT_CONFIGURED and the app works as
 * before. Never point this at a public mainnet.
 */
@Component
public class BlockchainService {

    private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);
    private static final Pattern ADDRESS = Pattern.compile("0x[0-9a-fA-F]{40}");
    private static final Pattern TX_HASH = Pattern.compile("0x[0-9a-fA-F]{64}");

    private final boolean enabled;
    private final String rpcUrl;
    private final String account;
    private final String network;
    private final String contract;
    private final Duration timeout;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicLong ids = new AtomicLong();

    public BlockchainService(@Value("${audit.blockchain.enabled:false}") boolean enabled,
            @Value("${audit.blockchain.rpc-url:}") String rpcUrl,
            @Value("${audit.blockchain.account:}") String account,
            @Value("${audit.blockchain.network-name:private-test-network}") String network,
            @Value("${audit.blockchain.contract:}") String contract,
            @Value("${audit.blockchain.timeout:PT10S}") Duration timeout) {
        boolean contractOk = contract.isBlank() || ADDRESS.matcher(contract).matches();
        boolean configured = enabled && !rpcUrl.isBlank() && ADDRESS.matcher(account).matches() && contractOk;
        if (enabled && !configured) {
            log.warn("audit.blockchain.enabled is true but rpc-url or account is missing or invalid; anchoring stays off");
        }
        this.enabled = configured;
        this.rpcUrl = rpcUrl;
        this.account = account;
        this.network = network;
        this.contract = contract.isBlank() ? null : contract;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
        if (configured) {
            log.info("Audit anchoring on {} via {} from {}{}", network, rpcUrl, account,
                    this.contract == null ? "" : " into contract " + this.contract);
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public String network() {
        return network;
    }

    /** True when proofs go into the AuditIntegrity contract rather than plain transactions. */
    public boolean contractMode() {
        return contract != null;
    }

    // ---- AuditIntegrity contract (blockchain/contracts/AuditIntegrity.sol) ----

    static final byte[] REGISTER = Keccak256.selector("register(bytes32,bytes32,uint64)");
    static final byte[] VERIFY = Keccak256.selector("verify(bytes32,bytes32)");
    static final byte[] GET_RECORD = Keccak256.selector("getRecord(bytes32)");

    /** The opaque on-chain reference of an event: SHA-256 of its id, so the chain holds no database ids. */
    public static String contractRef(AuditLog event) {
        return AuditHasher.sha256(event.id());
    }

    /** The event time rounded down to the hour, as unix seconds: coarse on purpose. */
    public static long coarseTimestamp(AuditLog event) {
        long seconds = event.timestamp().getEpochSecond();
        return seconds - Math.floorMod(seconds, 3600);
    }

    static String callData(byte[] selector, String... words) {
        StringBuilder data = new StringBuilder("0x").append(HexFormat.of().formatHex(selector));
        for (String w : words) {
            data.append(w);
        }
        return data.toString();
    }

    private static String word(long value) {
        return String.format("%064x", value);
    }

    private static String word32(String hex) {
        String h = hex.startsWith("0x") ? hex.substring(2) : hex;
        if (h.length() != 64) {
            throw new IllegalArgumentException("Expected 32 bytes");
        }
        return h.toLowerCase(java.util.Locale.ROOT);
    }

    private JsonNode ethCall(String data) {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("to", contract);
        call.put("data", data);
        return call("eth_call", List.of(call, "latest"));
    }

    /** What goes on chain for an event: the proof and nothing else. */
    public static Map<String, String> proofPayload(AuditLog event) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("app", "breastcare-audit");
        payload.put("v", "1");
        payload.put("ref", event.id());
        payload.put("hash", event.eventHash());
        payload.put("ts", AuditHasher.normalize(event.timestamp()).toString());
        return payload;
    }

    static String encode(Map<String, String> payload) {
        return "0x" + HexFormat.of().formatHex(AuditHasher.canonicalJson(payload).getBytes(StandardCharsets.UTF_8));
    }

    /** Sends the proof; returns the transaction hash. Throws BlockchainException if the node refuses or is down. */
    public String submit(AuditLog event) {
        requireEnabled();
        Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("from", account);
        tx.put("to", contract == null ? account : contract);
        tx.put("value", "0x0");
        tx.put("data", contract == null ? encode(proofPayload(event))
                : callData(REGISTER, word32(contractRef(event)), word32(event.eventHash()), word(coarseTimestamp(event))));
        JsonNode result = call("eth_sendTransaction", List.of(tx));
        String hash = result.asString("");
        if (!TX_HASH.matcher(hash).matches()) {
            throw new BlockchainException("The node did not return a transaction hash");
        }
        return hash;
    }

    /** @param mined false while the transaction waits for a block */
    public record Receipt(boolean mined, boolean success, Long blockNumber) {
    }

    public Receipt receipt(String txHash) {
        requireEnabled();
        JsonNode r = call("eth_getTransactionReceipt", List.of(txHash));
        if (r == null || r.isNull() || r.isMissingNode()) {
            return new Receipt(false, false, null);
        }
        boolean success = "0x1".equals(r.path("status").asString(""));
        return new Receipt(true, success, hexToLong(r.path("blockNumber").asString(null)));
    }

    /**
     * @param matches true when the transaction exists, is mined successfully, and its data is exactly this event's
     *                proof (same id, hash and timestamp)
     */
    public record Proof(String transactionHash, String network, boolean found, boolean mined, boolean success,
            Long blockNumber, Map<String, String> onChainPayload, boolean matches) {
    }

    public Proof proof(AuditLog event) {
        requireEnabled();
        if (contract != null) {
            return contractProof(event);
        }
        String txHash = event.blockchainReference();
        JsonNode tx = call("eth_getTransactionByHash", List.of(txHash));
        if (tx == null || tx.isNull() || tx.isMissingNode()) {
            return new Proof(txHash, network, false, false, false, null, Map.of(), false);
        }
        Map<String, String> onChain = decode(tx.path("input").asString(tx.path("data").asString("")));
        Receipt receipt = receipt(txHash);
        boolean matches = receipt.success() && onChain.equals(proofPayload(event));
        return new Proof(txHash, network, true, receipt.mined(), receipt.success(), receipt.blockNumber(), onChain, matches);
    }

    /** Reads the record back from the contract and asks it to verify the event's hash. */
    private Proof contractProof(AuditLog event) {
        String ref = contractRef(event);
        String record = stripHex(ethCall(callData(GET_RECORD, word32(ref))).asString(""));
        boolean verified = false;
        Map<String, String> onChain = new LinkedHashMap<>();
        boolean found = record.length() >= 192 && !record.substring(0, 64).equals("0".repeat(64));
        if (found) {
            onChain.put("contract", contract);
            onChain.put("ref", "0x" + ref);
            onChain.put("hash", record.substring(0, 64));
            onChain.put("ts", java.time.Instant.ofEpochSecond(Long.parseLong(record.substring(64, 128), 16)).toString());
            onChain.put("registrar", "0x" + record.substring(152, 192));
            String answer = stripHex(ethCall(callData(VERIFY, word32(ref), word32(event.eventHash()))).asString(""));
            verified = answer.length() == 64 && answer.endsWith("1");
        }
        String txHash = event.blockchainReference();
        Receipt receipt = txHash == null ? new Receipt(false, false, null) : receipt(txHash);
        return new Proof(txHash, network, found, receipt.mined(), receipt.success(), receipt.blockNumber(), onChain,
                found && verified && receipt.success());
    }

    private static String stripHex(String hex) {
        return hex.startsWith("0x") ? hex.substring(2) : hex;
    }

    private Map<String, String> decode(String hex) {
        try {
            String body = hex.startsWith("0x") ? hex.substring(2) : hex;
            JsonNode node = json.readTree(new String(HexFormat.of().parseHex(body), StandardCharsets.UTF_8));
            Map<String, String> out = new LinkedHashMap<>();
            for (String key : List.of("app", "v", "ref", "hash", "ts")) {
                if (node.has(key)) {
                    out.put(key, node.get(key).asString());
                }
            }
            return out;
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    private JsonNode call(String method, List<?> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", ids.incrementAndGet());
        body.put("method", method);
        body.put("params", params);
        HttpResponse<String> response;
        try {
            response = http.send(HttpRequest.newBuilder(URI.create(rpcUrl)).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new BlockchainException("The blockchain node could not be reached");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BlockchainException("Interrupted while calling the blockchain node");
        } catch (IllegalArgumentException e) {
            throw new BlockchainException("The blockchain RPC URL is not valid");
        }
        if (response.statusCode() != 200) {
            throw new BlockchainException("The blockchain node answered HTTP " + response.statusCode());
        }
        JsonNode reply = json.readTree(response.body());
        if (reply.has("error")) {
            throw new BlockchainException("The blockchain node refused " + method + ": " + reply.path("error").path("message").asString("error"));
        }
        return reply.path("result");
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new BlockchainException("Blockchain anchoring is not configured on this server.");
        }
    }

    private static Long hexToLong(String hex) {
        if (hex == null || !hex.startsWith("0x")) {
            return null;
        }
        return Long.parseLong(hex.substring(2), 16);
    }

    public static class BlockchainException extends RuntimeException {
        public BlockchainException(String message) {
            super(message);
        }
    }
}
