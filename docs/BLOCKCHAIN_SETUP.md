# Blockchain audit integrity

## Off-chain + on-chain

| Where | What |
|---|---|
| **MongoDB Atlas** (`audit_logs`) | The full audit event: type, user id, scan/report id, time, safe metadata (for reports: the PDF's SHA-256), `previousHash`, `eventHash`, and the blockchain transaction reference and status |
| **Chain** (`AuditIntegrity` contract) | Only `ref` = SHA-256 of the audit event id (opaque), `hash` = the event's SHA-256, and `timestamp` rounded down to the hour |

No names, emails, ids of patients or scans, images, reports or diagnoses go on chain. Use a local or permissioned test network only, never a public mainnet.

The backend computes every hash from its own stored data. Nothing the browser sends is trusted. How the event hash is built (canonical JSON, SHA-256, chain to the previous event) is in `docs/F20_AUDIT_BLOCKCHAIN_BACKUP_PRIVACY.md`, sections 5–6.

## The contract

`blockchain/contracts/AuditIntegrity.sol`:

- `register(bytes32 ref, bytes32 hash, uint64 timestamp)`: write-once.
  - Only accounts the owner allowed may register.
  - The same proof again is a no-op, so retries and two backend replicas are safe.
  - A different hash for an existing `ref` reverts with `ConflictingHash`.
- `getRecord(bytes32 ref)` returns `(hash, timestamp, registrar)`.
- `verify(bytes32 ref, bytes32 hash)` returns `true` only for the registered hash.
- `setRegistrar(address, bool)`: owner only.

## Local setup with Hardhat

```bash
cd blockchain
npm install
npx hardhat test                       # contract tests
npx hardhat node                       # terminal 1: chain on http://127.0.0.1:8545, prints test accounts
npx hardhat run scripts/deploy.js --network localhost   # terminal 2: prints the settings below
```

Put the printed values in `Backend/.env` and restart the backend:

```
AUDIT_BLOCKCHAIN_ENABLED=true
AUDIT_BLOCKCHAIN_RPC_URL=http://127.0.0.1:8545
AUDIT_BLOCKCHAIN_ACCOUNT=0xf39F...   # the deployer (an unlocked Hardhat account): it may register
AUDIT_BLOCKCHAIN_CONTRACT=0x5FbD...  # AuditIntegrity address
AUDIT_BLOCKCHAIN_NETWORK=localhost
```

- The backend sends `eth_sendTransaction` from that account, and the node signs. **The application holds no private key.**
- Hardhat's accounts are public test keys: never use them anywhere real.
- On a private Geth or Besu network, unlock a dedicated account on the node, or put a signing proxy in front of it.

Docker: `docker compose --profile blockchain up -d` starts the node as service `hardhat`. Deploy from your machine (`--network localhost`, port 8545 is published to 127.0.0.1), and use `AUDIT_BLOCKCHAIN_RPC_URL=http://hardhat:8545` for the backend container.

Hardhat's in-memory chain starts empty after a restart. Re-deploy the contract, and events anchored earlier will no longer be found on chain. Their status stays ANCHORED in MongoDB, but a full check then reports *Verification failed*. For a persistent dev chain, run `npx hardhat node` with state saving, or use a private Geth/Besu network.

Without `AUDIT_BLOCKCHAIN_CONTRACT`, the backend uses its earlier mode: a zero-value transaction to itself carrying the same proof as data.

## Audit hash generation and anchoring

1. An action happens. For example, the first download of a scan's PDF report writes a `REPORT_GENERATED` event with `reportSha256 = SHA-256(PDF bytes)`.
2. `AuditService` hashes the canonical event, links it to the previous one, and stores it with status `PENDING` (or `NOT_CONFIGURED` when anchoring is off).
3. `AuditAnchorService` (every `AUDIT_BLOCKCHAIN_POLL_INTERVAL`, or on demand) sends `register(...)`. The status becomes `ANCHORED` **only** when the receipt is mined with status 1.
   - A node error or revert makes it `FAILED` with the reason; retrying is safe.
   - An event that no longer matches its own hash is never anchored.
4. Anchoring failures never undo or block the medical action. The record simply stays PENDING or FAILED and is never shown as verified.

## Verifying a report

On a report page (`/reports/{id}`), the patient who owns it and any doctor it is shared with see **Report integrity** and a **Verify integrity** button. The backend (`POST /api/scans/{id}/integrity/verify`):

1. recomputes SHA-256 of the stored PDF and compares it with `reportSha256` in the `REPORT_GENERATED` record;
2. recomputes the record's own hash (it must equal `eventHash`);
3. if the record is not anchored yet, retries anchoring;
4. with anchoring on, calls `getRecord` and `verify(ref, eventHash)` on the contract.

| Status | Message |
|---|---|
| Verified | "This report matches its original recorded integrity hash." |
| Verified (off-chain) | Same, with a note that no blockchain is configured, so only the off-chain record was checked |
| Pending | "Blockchain verification is currently pending." |
| Verification failed | "The report content does not match the originally recorded integrity hash." Also logged as a warning and recorded as a `REPORT_VERIFICATION_FAILED` audit event |
| Not recorded / No report yet | The report predates integrity records, or has not been created |

- Other patients and other doctors get 404, signed-out visitors 401, and administrators 403 (they do not open medical records).
- Administrators verify any audit event under Admin → Security → Audit logs.
- The interface cannot change any record.

Try it: download a report, press *Verify integrity* (Verified). Then change the stored PDF in MongoDB, or stop the chain and re-deploy, and verify again (Verification failed).

## Tests

- `blockchain/test/AuditIntegrity.test.js` (Hardhat): register and verify, write-once, idempotent repeats, conflicting hash, registrar permissions, and that the function selectors match the backend's.
- `Backend/.../audit/Keccak256Test`: the backend's Keccak-256 against Ethereum vectors and known selectors.
- `Backend/.../audit/ReportIntegrityTest`, against a stand-in for the contract over JSON-RPC:
  - the record is saved off-chain with the PDF hash
  - exactly `register(sha256(eventId), eventHash, hour)` goes on chain
  - the original verifies
  - a changed PDF and a different on-chain hash fail
  - only the patient and shared doctors get in
- `Backend/.../audit/BlockchainLiveCheck`: by hand, against your running node:
  ```powershell
  $env:LIVE_BLOCKCHAIN_RPC_URL="http://127.0.0.1:8545"; $env:LIVE_BLOCKCHAIN_ACCOUNT="0x..."; $env:LIVE_BLOCKCHAIN_CONTRACT="0x..."
  cd Backend; .\mvnw.cmd test -Dtest=BlockchainLiveCheck
  ```
