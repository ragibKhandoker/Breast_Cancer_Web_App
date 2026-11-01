package com.cancerweb.backend.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Anchors a sample proof on a real local test node. Not part of the normal test run (the name does not end in
 * "Test"); run it by hand with a node started, e.g. {@code npx hardhat node}, {@code anvil} or {@code npx ganache}:
 *
 * <pre>
 * $env:LIVE_BLOCKCHAIN_RPC_URL = "http://127.0.0.1:8545"
 * $env:LIVE_BLOCKCHAIN_ACCOUNT = "0x...first unlocked account the node prints..."
 * $env:LIVE_BLOCKCHAIN_CONTRACT = "0x...AuditIntegrity address (optional)..."
 * .\mvnw.cmd test -Dtest=BlockchainLiveCheck
 * </pre>
 */
class BlockchainLiveCheck {

    @Test
    void anchorsAndProvesAProofOnALocalNode() throws Exception {
        String rpc = System.getenv("LIVE_BLOCKCHAIN_RPC_URL");
        String account = System.getenv("LIVE_BLOCKCHAIN_ACCOUNT");
        Assumptions.assumeTrue(rpc != null && account != null, "set LIVE_BLOCKCHAIN_RPC_URL and LIVE_BLOCKCHAIN_ACCOUNT");
        // Optional: LIVE_BLOCKCHAIN_CONTRACT = the AuditIntegrity address from blockchain/scripts/deploy.js.
        String contract = System.getenv().getOrDefault("LIVE_BLOCKCHAIN_CONTRACT", "");
        BlockchainService chain = new BlockchainService(true, rpc, account, "local-test-node", contract, Duration.ofSeconds(10));
        assertThat(chain.enabled()).isTrue();

        AuditLog draft = new AuditLog(new org.bson.types.ObjectId().toHexString(), 1, "USER_LOGIN", null, "SYSTEM", null, null,
                AuditHasher.normalize(Instant.now()), Map.of(), AuditHasher.GENESIS, null, BlockchainStatus.PENDING,
                null, null, null, null);
        AuditLog event = new AuditLog(draft.id(), 1, draft.eventType(), null, "SYSTEM", null, null, draft.timestamp(),
                Map.of(), AuditHasher.GENESIS, AuditHasher.hash(draft), BlockchainStatus.PENDING, null, null, null, null);

        String tx = chain.submit(event);
        BlockchainService.Receipt receipt = null;
        for (int i = 0; i < 30 && (receipt == null || !receipt.mined()); i++) {
            receipt = chain.receipt(tx);
            Thread.sleep(500);
        }
        assertThat(receipt.mined()).as("mined").isTrue();
        assertThat(receipt.success()).as("receipt status 1").isTrue();

        AuditLog anchored = new AuditLog(event.id(), 1, event.eventType(), null, "SYSTEM", null, null, event.timestamp(),
                Map.of(), event.previousHash(), event.eventHash(), BlockchainStatus.ANCHORED, tx, "local-test-node",
                Instant.now(), null);
        BlockchainService.Proof proof = chain.proof(anchored);
        System.out.println("LIVE PROOF: tx " + tx + " block " + proof.blockNumber() + " payload " + proof.onChainPayload());
        assertThat(proof.matches()).isTrue();
    }
}
