package com.cancerweb.backend.audit;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cancerweb.backend.audit.AuditService.Actor;
import com.cancerweb.backend.audit.BlockchainService.BlockchainException;
import com.cancerweb.backend.model.Prediction;
import com.cancerweb.backend.security.CurrentUser;
import com.cancerweb.backend.service.PredictionService;
import com.cancerweb.backend.service.ScanReportService;

/**
 * Has a scan's PDF report changed since it was made? Off-chain, the REPORT_GENERATED audit event holds the PDF's
 * SHA-256 and is itself hash-chained; on-chain (when configured), that event's hash is registered. A check
 * recomputes everything on the backend: the stored PDF's hash against the record, the record against its own hash,
 * and, for a full check, the record against the blockchain. Nothing the browser sends is trusted.
 *
 * <p>Who may check: the same people who may open the report (its patient, or a doctor it is shared with).
 */
@Service
public class ReportIntegrityService {

    private static final Logger log = LoggerFactory.getLogger(ReportIntegrityService.class);

    public enum Status {
        /** The PDF matches its record, the record matches its hash, and the blockchain proof matches. */
        VERIFIED,
        /** The PDF and its record match, but no blockchain is configured, so only the off-chain record was checked. */
        OFF_CHAIN_VERIFIED,
        /** The PDF and its record match; the blockchain proof is not confirmed yet. */
        PENDING,
        /** The PDF or its record no longer matches, or the blockchain holds a different proof. */
        FAILED,
        /** The report was made before integrity records existed. */
        NOT_RECORDED,
        /** No PDF report has been made for this scan yet. */
        NO_REPORT,
    }

    public record Integrity(Status status, String message, String recordedReportSha256, boolean reportMatches,
            boolean recordIntact, BlockchainStatus blockchainStatus, String transactionReference, String network,
            Instant recordedAt, Instant anchoredAt, Boolean onChainMatches, Instant checkedAt) {
    }

    private final PredictionService predictions;
    private final ScanReportService reports;
    private final AuditService audit;
    private final AuditAnchorService anchors;
    private final BlockchainService blockchain;

    public ReportIntegrityService(PredictionService predictions, ScanReportService reports, AuditService audit,
            AuditAnchorService anchors, BlockchainService blockchain) {
        this.predictions = predictions;
        this.reports = reports;
        this.audit = audit;
        this.anchors = anchors;
        this.blockchain = blockchain;
    }

    /**
     * @param full also check the blockchain (and retry anchoring a waiting record); a quick status uses the stored
     *             anchoring state instead
     */
    public Integrity check(String scanId, CurrentUser viewer, boolean full) throws IOException {
        Prediction scan = predictions.findAccessible(scanId, viewer);
        Instant now = Instant.now();
        if (scan.reportFileId() == null) {
            return new Integrity(Status.NO_REPORT, "No PDF report has been made yet. Download it once to create and record it.",
                    null, false, false, null, null, null, null, null, null, now);
        }
        AuditLog record = audit.latestFor(AuditEventType.REPORT_GENERATED, scan.id());
        if (record == null) {
            return new Integrity(Status.NOT_RECORDED, "This report was made before integrity records existed, so it cannot be checked.",
                    null, false, false, null, null, null, null, null, null, now);
        }
        byte[] pdf = reports.storedReport(scan);
        String recorded = record.metadata().get("reportSha256");
        boolean reportMatches = pdf != null && AuditHasher.sha256Bytes(pdf).equals(recorded);
        boolean recordIntact = AuditHasher.hash(record).equals(record.eventHash());

        if (full && blockchain.enabled() && record.blockchainStatus() != BlockchainStatus.ANCHORED && recordIntact) {
            // Safe retry: sends the proof if it never went out, or re-checks a sent one. Never marks it verified itself.
            record = anchors.anchor(record.id());
        }
        Boolean onChain = null;
        if (full && blockchain.enabled() && record.blockchainStatus() == BlockchainStatus.ANCHORED) {
            try {
                onChain = blockchain.proof(record).matches();
            } catch (BlockchainException e) {
                log.warn("Report {} integrity: the blockchain could not be checked ({})", scan.id(), e.getMessage());
            }
        }

        Status status;
        String message;
        if (!reportMatches || !recordIntact || Boolean.FALSE.equals(onChain)) {
            status = Status.FAILED;
            message = "The report content does not match the originally recorded integrity hash.";
            if (full) {
                log.warn("Report {} failed its integrity check (report matches: {}, record intact: {}, on-chain: {})",
                        scan.id(), reportMatches, recordIntact, onChain);
                audit.record(AuditEventType.REPORT_VERIFICATION_FAILED, Actor.of(viewer), "SCAN", scan.id(),
                        Map.of("reportMatches", String.valueOf(reportMatches), "recordIntact", String.valueOf(recordIntact),
                                "onChain", String.valueOf(onChain)));
            }
        } else if (record.blockchainStatus() == BlockchainStatus.NOT_CONFIGURED || !blockchain.enabled()) {
            status = Status.OFF_CHAIN_VERIFIED;
            message = "This report matches its original recorded integrity hash. Blockchain anchoring is not set up, so only the off-chain record was checked.";
        } else if (record.blockchainStatus() == BlockchainStatus.ANCHORED && (onChain == null ? !full : onChain)) {
            status = Status.VERIFIED;
            message = "This report matches its original recorded integrity hash.";
        } else {
            status = Status.PENDING;
            message = "Blockchain verification is currently pending.";
        }
        return new Integrity(status, message, recorded, reportMatches, recordIntact, record.blockchainStatus(),
                record.blockchainReference(), record.blockchainNetwork(), record.timestamp(), record.blockchainAnchoredAt(),
                onChain, now);
    }
}
