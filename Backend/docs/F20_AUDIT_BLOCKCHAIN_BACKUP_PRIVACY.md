# F20: Audit logs, blockchain proofs, backup and privacy

## 1. Overview

- **Audit log.** The backend records important actions (scans, report downloads, reviews, role changes, logins, account and backup actions) as append-only events.
- **Hash chain.** Each event gets a SHA-256 hash over its canonical form, and each hash includes the previous event's hash. A changed, deleted or inserted event is therefore detectable.
- **Blockchain proofs (optional).** An event's proof (hash, timestamp and opaque event id, nothing else) can be anchored on a private or test Ethereum network.
- **Backups.** The whole MongoDB database is backed up into one encrypted, integrity-protected file. A backup can be restore-tested in a separate database.
- **Admin dashboard.** Administrators see two new tabs under *Security*: **Audit logs** and **Backups**.

What differs from the original F20 brief, and why:

| Brief | Here | Why |
|---|---|---|
| PostgreSQL, `pg_dump` / `pg_restore` | MongoDB. The backend writes the backup itself: Extended JSON → gzip → AES-256-CTR + HMAC-SHA256 | The project uses MongoDB Atlas, and this needs no extra tools on the server |
| `/api/audit-logs`, `/api/backups` | `/api/admin/audit-logs`, `/api/admin/backups` | Every `/api/admin/**` path already requires the ADMIN role in `SecurityConfig` |
| `REVIEW_UPDATED` on report reviews | Breast Surgeon profile reviews and account-verification reviews | The site has no report-review workflow. Those two are the reviews that exist |
| Hyperledger Fabric or private Ethereum | Private/test Ethereum over JSON-RPC (Hardhat, Anvil, Ganache, private Geth/Besu) | No extra library. The node signs with its own unlocked dev account, so the app holds no private key |

## 2. Architecture

```
 Controllers / services (backend)                Admin dashboard (Vue)
   PredictionService ─ SCAN_*                      AdminAuditPanel  ──► /api/admin/audit-logs/**
   ScanReportService ─ REPORT_DOWNLOADED           AdminBackupPanel ──► /api/admin/backups/**
   UserService / AuthService / ProfileService
   AdminUser/Surgeon/Console controllers
            │ record(type, actor, target, safe metadata)
            ▼
   AuditService ── AuditHasher (canonical JSON + SHA-256, chained)
            │ insert only                     ▲ verify / verify-chain
            ▼                                 │
   MongoDB: audit_logs ◄── AuditAnchorService ── BlockchainService ── JSON-RPC ──► private test node
                                    (manual + scheduled)                 (hash, ts, ref only)

   BackupService ── BackupCrypto ──► backups/backup_YYYY-MM-DD_HH-MM-SS.bcbk (+ .meta.json)
        │  restore test ──► <db>_restore_test (checked, then dropped)
        └─ BackupCommandRunner (Backend/scripts/backup.*, restore-test.*)
```

Code: `Backend/src/main/java/com/cancerweb/backend/audit/` and `.../backup/`.

## 3. Database schema: `audit_logs`

| Field | Type | Notes |
|---|---|---|
| `_id` | ObjectId (string) | Event id, also the opaque reference sent on chain |
| `sequence` | long | Position in the chain, unique index `sequence_unique` |
| `eventType` | string | See the list below |
| `actorId` / `actorRole` | string | Who did it. `null`/`ANONYMOUS` for a failed login, `null`/`SYSTEM` for scheduled jobs |
| `targetType` / `targetId` | string | `SCAN`, `USER`, `SURGEON_PROFILE`, `BACKUP`, `SITE_SETTINGS`, and the record's opaque id |
| `timestamp` | date | Millisecond precision (as hashed) |
| `metadata` | map&lt;string,string&gt; | Codes, roles, statuses, counts and ids only (rules in section 13) |
| `previousHash` | hex | Hash of event `sequence - 1`. The first event uses 64 zeros |
| `eventHash` | hex | SHA-256 of the canonical event |
| `blockchainStatus` | enum | `NOT_CONFIGURED`, `PENDING`, `ANCHORED`, `FAILED` |
| `blockchainReference` / `blockchainNetwork` / `blockchainAnchoredAt` / `blockchainError` | | Not hashed; the only fields ever updated |

Indexes are created at startup by `DatabaseInitializer`. MongoDB needs no migration: the collection appears with its first event.

Event types: `SCAN_CREATED`, `SCAN_DELETED`, `SCAN_SHARED`, `SCAN_UNSHARED`, `REPORT_DOWNLOADED`, `REVIEW_UPDATED`, `ADMIN_ROLE_CHANGED`, `ACCOUNT_CREATED_BY_ADMIN`, `ACCOUNT_DISABLED`, `ACCOUNT_ENABLED`, `PASSWORD_RESET_BY_ADMIN`, `PASSWORD_CHANGED`, `USER_LOGIN`, `USER_LOGIN_FAILED` (existing accounts only, by id), `USER_LOGOUT`, `SITE_SETTINGS_UPDATED`, `BACKUP_CREATED`, `BACKUP_DELETED`, `BACKUP_RESTORE_TESTED`.

## 4. Audit event lifecycle

1. The action succeeds in the backend (for example `PredictionService.analyse` saves the scan).
2. That code calls `AuditService.record(...)`. Nothing the browser sends decides whether an event is written.
3. `AuditService` checks the metadata against the privacy rules, then reads the last event and computes the next `sequence` and `previousHash`. It hashes the canonical event and inserts it. If two writers race, the unique `sequence` index rejects one, and it retries on top of the new last event.
4. The status is `NOT_CONFIGURED`, or `PENDING` when anchoring is on. With anchoring on, a scheduled job (and the admin's *Anchor* button) sends the proof and waits for the receipt.
5. Events are never edited or deleted through the application. There is no update or delete endpoint, and `AuditService` has no such method. Only the blockchain fields change.

If MongoDB refuses an audit write, the error is logged at ERROR level and the user's action still completes. Watch the logs for "Could not write audit event".

## 5. SHA-256 hash generation

`AuditHasher.canonicalEvent` builds the following object and `canonicalJson` serialises it:

```json
{"actorId":…,"actorRole":…,"eventType":…,"id":…,"metadata":{…},"previousHash":…,"sequence":7,"targetId":…,"targetType":…,"timestamp":"2026-11-01T10:30:00.123Z"}
```

Serialisation rules:

- Keys are sorted at every level. There is no whitespace.
- Strings are JSON-escaped, integers are plain, and absent values are `null`.
- Timestamps are ISO-8601 UTC, truncated to milliseconds (Java `Instant.toString`, which leaves out a `.000` fraction).

`eventHash = SHA-256(UTF-8 bytes)`, in lowercase hex. A unit test checks the example event from the brief against Python's `json.dumps(sort_keys=True, separators=(",", ":"))` + `hashlib.sha256`, so other tools can reproduce the hash. MD5 and SHA-1 are not used.

## 6. Hash verification

- `GET /api/admin/audit-logs/{id}/verify` recomputes the hash from the stored fields. It returns:
  - `status`: `VERIFIED` (equal) or `TAMPERED`.
  - `chainStatus`: `LINKED` when `previousHash` equals the stored hash of event `sequence - 1`, else `BROKEN` (that event is missing or different).
- `GET /api/admin/audit-logs/verify-chain` walks every event in order. It returns the first problem: a missing sequence number, a changed event, or a broken link.
- Every row in the list also carries `hashVerified`.

Verification always runs on the backend.

## 7. Blockchain architecture

- `BlockchainService` talks JSON-RPC to the configured node:
  - `eth_sendTransaction` sends a zero-value transaction from the account to itself, with `data` = hex of the canonical JSON `{"app":"breastcare-audit","hash":…,"ref":<event id>,"ts":…,"v":"1"}`.
  - `eth_getTransactionReceipt` reads the receipt.
  - `eth_getTransactionByHash` reads the transaction back for proofs.
- `AuditAnchorService` sets `ANCHORED` only when the receipt is mined with status `0x1`. A reverted transaction or a node error gives `FAILED` with the reason. A sent but unmined transaction stays `PENDING` with its reference and is checked again.
- An event that no longer matches its own hash is never anchored.
- `GET /{id}/blockchain-proof` re-reads the transaction from the chain. It reports `matches: true` only if the on-chain payload equals this event's proof and the receipt succeeded.
- Off by default. If the node is down, only anchoring fails; the rest of the site works.
- Never on chain: patient names or ids, scan ids, images, reports, results, emails, passwords or tokens. The `ref` is the audit event's own id.

## 8. Blockchain setup (local test network)

1. Start a dev node:
   - `npx hardhat node`, or `anvil`, or `npx ganache`
   - It listens on `http://127.0.0.1:8545` and prints unlocked accounts.
2. In `Backend/.env`:
   ```
   AUDIT_BLOCKCHAIN_ENABLED=true
   AUDIT_BLOCKCHAIN_RPC_URL=http://127.0.0.1:8545
   AUDIT_BLOCKCHAIN_ACCOUNT=0x<first account printed by the node>
   AUDIT_BLOCKCHAIN_NETWORK=hardhat-local
   ```
3. Restart the backend. New events start `PENDING` and are anchored within `AUDIT_BLOCKCHAIN_POLL_INTERVAL`. Older `NOT_CONFIGURED` events are anchored too.
4. Check by hand: open Admin → Audit logs, pick an event, then press *Anchor on blockchain* and *Check on-chain proof*. Or run the live check:
   ```powershell
   $env:LIVE_BLOCKCHAIN_RPC_URL = "http://127.0.0.1:8545"
   $env:LIVE_BLOCKCHAIN_ACCOUNT = "0x..."
   cd Backend; .\mvnw.cmd test -Dtest=BlockchainLiveCheck
   ```

Use only a private or test network. Never point this at a public mainnet. The node's dev accounts must not hold real funds.

## 9. Environment variables

| Variable | Default | Meaning |
|---|---|---|
| `BACKUP_ENCRYPTION_KEY` | (none) | 32 random bytes, base64. **Required** for backups. Keep a copy separately; without it backups cannot be read |
| `BACKUP_DIR` | `backups` (Backend/backups) | Folder for backups, outside the website's files. Git-ignored |
| `BACKUP_SCHEDULE` | `-` (off) | Spring cron in UTC, e.g. `0 0 2 * * *` |
| `BACKUP_RETENTION_ENABLED` | `false` | Delete old backups per the policy below |
| `BACKUP_RETENTION_DAILY` / `_WEEKLY` / `_MONTHLY` | `7` / `4` / `3` | Each must be 1..3650, otherwise nothing is deleted |
| `BACKUP_RESTORE_ENABLED` | `false` | Allow restore tests (test servers only) |
| `BACKUP_RESTORE_KEEP_TEST_DATABASE` | `false` | Keep `<db>_restore_test` after the check |
| `AUDIT_BLOCKCHAIN_ENABLED` | `false` | Turn on anchoring |
| `AUDIT_BLOCKCHAIN_RPC_URL` | | Node URL |
| `AUDIT_BLOCKCHAIN_ACCOUNT` | | Unlocked account address on the node |
| `AUDIT_BLOCKCHAIN_NETWORK` | `private-test-network` | Label stored with each proof |
| `AUDIT_BLOCKCHAIN_POLL_INTERVAL` | `PT30S` | How often waiting events are sent |

Make a key with `openssl rand -base64 32`, or in PowerShell: `[Convert]::ToBase64String((1..32 | % { [byte](Get-Random -Max 256) }))`. For production, prefer `openssl` or another cryptographic generator.

## 10. Creating backups

Each backup is one file `backup_YYYY-MM-DD_HH-MM-SS.bcbk` (UTC) plus a `.meta.json` with counts, size and SHA-256 (no personal data). It contains every collection, including the images (GridFS) and the audit log.

- **Dashboard:** Admin → Security → Backups → *Create backup*.
- **Command line:** `powershell -File Backend\scripts\backup.ps1` or `sh Backend/scripts/backup.sh`. Exit code 0 means the file was written and passed its integrity check.
- **Scheduled, built-in:** set `BACKUP_SCHEDULE=0 0 2 * * *`.
- **Scheduled, Windows:** a Task Scheduler task running `powershell -ExecutionPolicy Bypass -File <path>\Backend\scripts\backup.ps1` daily.
- **Scheduled, Linux:** cron `0 2 * * * cd /srv/Cancer_web && sh Backend/scripts/backup.sh`.

A backup is reported as created only after the file is complete and its HMAC verifies.

Protect the files:

- On Linux and macOS the folder is created `0700` and the files `0600`.
- On Windows, restrict the folder's ACL to the service account and administrators.
- Never copy backups into `Frontend/` or `Backend/src/main/resources/static`.
- Never commit them: `Backend/.gitignore` excludes `backups/` and `*.bcbk`.
- Store an off-site copy encrypted, as it already is, and the key separately.

## 11. Restoring (test environment only)

1. **Pick a backup:** Admin → Backups lists them with time, size, record count and SHA-256. *Verify* re-checks the integrity tag; a changed, truncated or wrong-key file is refused.
2. **Restore test:** on a test server, set `BACKUP_RESTORE_ENABLED=true`, then press *Restore test*, or run `Backend\scripts\restore-test.ps1 <file>` (`sh Backend/scripts/restore-test.sh <file>`). The backend:
   - checks the tag
   - drops and recreates `<database>_restore_test` (never the live database: other names are refused)
   - inserts every document
   - compares each collection's count with the backup's footer
   - checks that `users`, `predictions`, `audit_logs` and `breast_surgeon_profiles` exist
   - re-verifies every restored audit event's hash
3. **Result:** the report shows passed or failed, with counts and problems. The test database is then dropped, unless `BACKUP_RESTORE_KEEP_TEST_DATABASE=true`. A `BACKUP_RESTORE_TESTED` audit event records the outcome, and the dashboard shows the last one.

Keep `BACKUP_RESTORE_ENABLED=false` on the production server. To really recover production after an incident:

1. Restore-test the chosen backup first.
2. With `BACKUP_RESTORE_KEEP_TEST_DATABASE=true`, keep the verified copy.
3. Point a staging backend at it (`MONGODB_URI` → that database) and check the site.
4. Only then switch production's `MONGODB_URI`, or copy it with Atlas tools, as a separately approved operation.

The application never overwrites a live database.

## 12. Backup retention

Grandfather-father-son retention, in UTC: keep the newest backup of each of the last 7 days that have one, of the last 4 ISO weeks, and of the last 3 months. The newest backup is always kept. The policy runs after each new backup, only when `BACKUP_RETENTION_ENABLED=true` and every count is valid. Each deletion is recorded as `BACKUP_DELETED`.

## 13. Data privacy policy

| Data | Where | Who can access | Kept |
|---|---|---|---|
| Accounts (name, email, password hash, profile) | `users` | The user; administrators (no medical records) | Until the account is removed by an administrator |
| Mammograms, results, heatmaps, PDF reports | `predictions`, GridFS | The owning patient; doctors they share with | Until the patient deletes the report |
| Appointments, messages, notifications | own collections | The people involved; administrators for messages | As long as the account exists |
| Audit events | `audit_logs` | Administrators only (read-only) | Suggested default: **3 years**. Confirm with your institution's data-protection rules |
| Backups | `BACKUP_DIR`, encrypted | Server operators with the key | Per the retention policy (default: 7 daily / 4 weekly / 3 monthly) |
| Blockchain proofs | private test chain | Node operators | As long as the chain exists; contains no personal data |

Enforced in code:

- Role checks in `SecurityConfig`: `/api/admin/**` requires ADMIN, and record-level checks are in the services.
- Audit metadata is filtered by `AuditService.safeMetadata`. Keys containing password, token, secret, email, name, phone, address, note, message, label, result, probability, diagnosis, image, birth or gender are refused, and values must be short codes or ids, never free text or emails.
- Failed logins store the account id, never the typed email.
- No request bodies, passwords or tokens are logged.
- Backups are encrypted, with an HMAC to detect tampering.
- Secrets come only from environment variables or `Backend/.env`, which is git-ignored.

## 14. Data deletion

- **Patient deletes a report:** the image, cached PDF and record are removed. A `SCAN_DELETED` event remains, with only the deleted scan's id and the patient's account id: no image, file name or result.
- **Audit events** are not deleted by the application. After the retention period, an operator removes events older than the cut-off in a separately approved maintenance step. Deleting from the start of the chain keeps the rest verifiable from the first remaining event's `previousHash`. Record the cut-off and the last removed hash in the maintenance log.
- **Backups** are removed by the retention policy. Deleted personal data disappears from backups as the old files expire.
- **Account removal** (not available in the app yet) should delete or anonymise the user and their records. Audit events keep only the opaque id, which then no longer resolves to a person.

## 15. API

All need the ADMIN role (401 signed out, 403 for other roles).

| Method | Path | |
|---|---|---|
| GET | `/api/admin/audit-logs?eventType&actorId&targetId&from&to&blockchainStatus&page&size` | Page of events (newest first, `size` ≤ 100); dates `2026-11-01` or ISO instants |
| GET | `/api/admin/audit-logs/stats` | Counts by type and by blockchain status, and anchoring config |
| GET | `/api/admin/audit-logs/{id}` | Event plus verification |
| GET | `/api/admin/audit-logs/{id}/verify` | `VERIFIED` or `TAMPERED`, `LINKED` or `BROKEN` |
| GET | `/api/admin/audit-logs/verify-chain` | Whole-chain check |
| POST | `/api/admin/audit-logs/{id}/anchor` | Send or re-check the proof (503 when anchoring is off or the node fails) |
| GET | `/api/admin/audit-logs/{id}/blockchain-proof` | On-chain proof and whether it matches |
| GET | `/api/admin/backups` | Backups, newest first |
| GET | `/api/admin/backups/status` | Last backup, retention, encryption, last restore test |
| POST | `/api/admin/backups` | Create a backup (503 without `BACKUP_ENCRYPTION_KEY`) |
| POST | `/api/admin/backups/{file}/verify` | Integrity check (422 if changed) |
| POST | `/api/admin/backups/restore-test` `{file, targetDatabase?}` | 403 unless `BACKUP_RESTORE_ENABLED=true`. Target must end with `_restore_test` |

Backup files are never downloadable through the API.

## 16. Testing

Run `cd Backend; .\mvnw.cmd test` and `cd Frontend; npm test`.

| Test | Covers |
|---|---|
| `audit/AuditHasherTest` | Canonical JSON, the SHA-256 example against Python, changes break the hash, blockchain fields not hashed, metadata privacy rules |
| `audit/AuditLogApiTest` | Login, failed login and logout events; role-change, review and report-download events; no names, emails, file names or results anywhere in the log; only admins can read; no edit or delete; filters and pagination; tamper and missing-event detection |
| `PredictionWorkflowTest` | `SCAN_CREATED` and `SCAN_DELETED` for a real upload |
| `audit/BlockchainAnchoringTest` | Against a stand-in JSON-RPC node: only `{app,v,ref,hash,ts}` is sent; `ANCHORED` only on a successful receipt; revert and down node give `FAILED` while the app keeps working; changed events are not anchored; unreachable node |
| `audit/BlockchainLiveCheck` | Manual, against a real local node (section 8) |
| `backup/BackupApiTest` | Encrypted file (no readable emails, names or hashes), meta file, audit event, integrity failure on a changed byte, restore test into `_restore_test` with matching counts and audit hashes (then dropped), live-database and path-traversal refusals, admin-only |
| `backup/RetentionPolicyTest` | Daily, weekly and monthly selection; invalid policies |
| `Frontend/tests/adminSecurity.spec.js` | Audit tab (cards, rows, hash state, filters, paging, details, anchoring, proof, chain check) and Backups tab (status, restore report, no key, restore off) |

## Not done / needs infrastructure

- **Live anchoring** was tested against a stand-in node, not a real chain. Run `BlockchainLiveCheck` once a Hardhat, Anvil or Ganache node is available.
- **Real backups** of the Atlas database need `BACKUP_ENCRYPTION_KEY` set. Then run `Backend/scripts/backup.ps1` and a restore test on a test server.
- **Account deletion** is not a feature of the site yet (section 14).
