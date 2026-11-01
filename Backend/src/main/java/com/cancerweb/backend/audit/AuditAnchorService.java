package com.cancerweb.backend.audit;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.cancerweb.backend.audit.BlockchainService.BlockchainException;
import com.cancerweb.backend.audit.BlockchainService.Receipt;

/**
 * Moves audit events through the blockchain states. An event becomes ANCHORED only after its transaction is mined
 * with a successful receipt; a sent but unmined transaction stays PENDING with its reference and is checked again
 * later; any error marks it FAILED with the reason (it can be retried). If the network is down, nothing else in the
 * app is affected.
 */
@Service
public class AuditAnchorService {

    private static final Logger log = LoggerFactory.getLogger(AuditAnchorService.class);
    private static final int RECEIPT_CHECKS = 10;
    private static final long RECEIPT_WAIT_MS = 300;

    private final AuditService audit;
    private final BlockchainService blockchain;

    public AuditAnchorService(AuditService audit, BlockchainService blockchain) {
        this.audit = audit;
        this.blockchain = blockchain;
    }

    /** Sends one event's proof (or re-checks a sent one) and returns the event as stored afterwards. */
    public AuditLog anchor(String id) {
        AuditLog event = audit.get(id);
        if (!blockchain.enabled()) {
            throw new BlockchainException("Blockchain anchoring is not configured on this server.");
        }
        if (event.blockchainStatus() == BlockchainStatus.ANCHORED) {
            return event;
        }
        if (event.blockchainStatus() == BlockchainStatus.PENDING && event.blockchainReference() != null) {
            return confirm(event.id(), event.blockchainReference());
        }
        if (!AuditHasher.hash(event).equals(event.eventHash())) {
            // Never put a proof on chain for an event that no longer matches its own hash.
            audit.setBlockchain(id, BlockchainStatus.FAILED, null, blockchain.network(), null,
                    "The event failed hash verification, so it was not anchored.");
            return audit.get(id);
        }
        try {
            String tx = blockchain.submit(event);
            audit.setBlockchain(id, BlockchainStatus.PENDING, tx, blockchain.network(), null, null);
            return confirm(id, tx);
        } catch (BlockchainException e) {
            log.warn("BLOCKCHAIN anchoring failed for audit event {}: {}", id, e.getMessage());
            audit.setBlockchain(id, BlockchainStatus.FAILED, null, blockchain.network(), null, e.getMessage());
            return audit.get(id);
        }
    }

    private AuditLog confirm(String id, String tx) {
        try {
            for (int i = 0; i < RECEIPT_CHECKS; i++) {
                Receipt receipt = blockchain.receipt(tx);
                if (receipt.mined()) {
                    if (receipt.success()) {
                        audit.setBlockchain(id, BlockchainStatus.ANCHORED, tx, blockchain.network(), Instant.now(), null);
                    } else {
                        audit.setBlockchain(id, BlockchainStatus.FAILED, tx, blockchain.network(), null,
                                "The transaction was mined but failed.");
                    }
                    return audit.get(id);
                }
                Thread.sleep(RECEIPT_WAIT_MS);
            }
        } catch (BlockchainException e) {
            // Sent, but the receipt could not be read: stay PENDING with the reference and check again later.
            log.warn("Could not read the receipt for audit event {}: {}", id, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return audit.get(id);
    }

    public BlockchainService.Proof proof(String id) {
        AuditLog event = audit.get(id);
        if (!blockchain.enabled()) {
            throw new BlockchainException("Blockchain anchoring is not configured on this server.");
        }
        if (event.blockchainReference() == null) {
            throw new BlockchainException("This event has not been sent to the blockchain yet.");
        }
        return blockchain.proof(event);
    }

    /** With anchoring on, sends waiting events in order, a few at a time. Off: does nothing. */
    @Scheduled(fixedDelayString = "${audit.blockchain.poll-interval:PT30S}", initialDelayString = "${audit.blockchain.poll-interval:PT30S}")
    public void anchorWaiting() {
        if (!blockchain.enabled()) {
            return;
        }
        for (AuditLog event : audit.findForAnchoring(20)) {
            AuditLog after = anchor(event.id());
            if (after.blockchainStatus() == BlockchainStatus.FAILED) {
                // The network is probably down; try again on the next run instead of failing every event now.
                break;
            }
        }
    }
}
