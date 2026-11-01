package com.cancerweb.backend.audit;

/**
 * Where an audit event's hash stands on the blockchain. NOT_CONFIGURED: anchoring is turned off. PENDING: waiting to
 * be sent, or sent and waiting for a confirmed receipt. ANCHORED: a mined transaction with a successful receipt
 * carries the hash. FAILED: the last attempt failed (it can be retried).
 */
public enum BlockchainStatus {
    NOT_CONFIGURED,
    PENDING,
    ANCHORED,
    FAILED,
}
