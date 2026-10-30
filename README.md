# Breast Cancer Detection Web App

A patient signs in, uploads a mammogram and gets an estimated malignancy probability. Results and images are saved in MongoDB Atlas, visible only to that patient and the doctors they choose to share them with.

```
Browser ──▶ Spring Boot (:8081)
              ├─ serves the Vue website
              ├─ Spring Security: JWT in an HttpOnly cookie, roles PATIENT / DOCTOR / ADMIN
              ├─ /api/auth, /api/predictions, /api/admin, /api/doctors  REST API
              ├─ ONNX Runtime ── ML-Service/weights/model.onnx
              └─ MongoDB Atlas (users, predictions + GridFS images, revoked_tokens, contact_messages)

ML-Service (Python) is only for training and exporting the model, not for running the site.
```

## Architecture (MVC)

| Layer | Where | What it does |
|---|---|---|
| **Model** | `Backend/.../model/` (`User`, `Role`, `Prediction`, `RevokedToken`, `MlPrediction`), `Backend/.../repository/` | Data stored in MongoDB Atlas and the data access to it |
| **View** | `Frontend/src/views/` (pages), `Frontend/src/components/` (layout, home, prediction, auth, common) | The Vue website the user sees |
| **Controller** | `Backend/.../controller/` (`AuthController`, `PredictionController`, `AdminUserController`, `DoctorController`, `ContactController`, `SpaController`) | Receives requests and returns JSON or pages |
| Service | `Backend/.../service/` (`AuthService`, `UserService`, `PredictionService`, `OnnxPredictor`) | Business logic, ownership checks and model inference |
| Security | `Backend/.../security/` (`SecurityConfig`, `JwtService`, `AuthCookies`, `TokenUserConverter`), `Frontend/src/auth/` | Login tokens, role rules, CSRF, CORS; frontend session state and route guards |
| Supporting | `Backend/.../exception/`, `Backend/.../config/` (startup checks, indexes, first admin), `Frontend/src/services/` (API calls), `Frontend/src/router/` | Error handling, startup report, HTTP client, page routing |

Flow: View → `services/*.js` → Controller → Service → Repository/Model → MongoDB, and back.

> **Disclaimer:** For research and education only. This is not a medical device and does not replace a radiologist.

## Requirements

- Java 21 (JDK). No Maven install needed: use the bundled `mvnw.cmd`.
- Node.js 20+
- Python 3.10+ (only to train or export the model)
- A MongoDB Atlas cluster (the free M0 tier is enough)

## 1. Connect MongoDB Atlas

1. Sign in at https://cloud.mongodb.com and open your project.
2. **Cluster**: reuse the existing cluster, or **Create → M0 (free)**. One cluster can hold many databases.
3. **Database Access → Add New Database User**: choose *Password*, a user name and a strong password, and the role *Read and write to any database* (or limit it to `breast_cancer`).
   This **database user** is what the backend logs in with. It is **not** the email and password you use to sign in to the Atlas website.
4. **Network Access → Add IP Address**: add the IP of the computer or server that runs the backend (*Add Current IP Address* on your own PC). `0.0.0.0/0` allows any IP; use it only for short-lived testing.
5. **Database → Connect → Drivers**: copy the connection string. It looks like
   `mongodb+srv://<username>:<password>@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority`
6. Copy `Backend/.env.example` to `Backend/.env`. Paste the string as `MONGODB_URI`, replace `<username>` and `<password>` with the database user from step 3, and put the database name `breast_cancer` right after `.net/`:
   ```
   MONGODB_URI=mongodb+srv://appuser:MyP%40ss@cluster0.xxxxx.mongodb.net/breast_cancer?retryWrites=true&w=majority&serverSelectionTimeoutMS=5000
   ```
   URL-encode special characters in the password (`@` → `%40`, `:` → `%3A`, `/` → `%2F`, `?` → `%3F`, `#` → `%23`).
7. Add a `JWT_SECRET` (see the table below) and, the first time, `ADMIN_EMAIL` / `ADMIN_PASSWORD`.
8. Start the backend (section 3). The console shows `[OK] MongoDB connected -> <cluster> / database 'breast_cancer'`. If Atlas is unreachable the backend stops with a message saying what to check (usually the Network Access list). The connection string is never printed.

You do not create the database or collections by hand: MongoDB creates them on first use, and the backend creates its indexes at every start (it is safe to repeat).

### Environment variables (`Backend/.env`, git-ignored)

| Variable | Required | Example / default | Purpose |
|---|---|---|---|
| `MONGODB_URI` | yes | `mongodb+srv://<user>:<password>@<cluster>.mongodb.net/breast_cancer?...` | Atlas connection, including the database name |
| `JWT_SECRET` | yes | 48+ random characters | Signs login tokens. Generate with `openssl rand -base64 48` (see `.env.example` for PowerShell). Changing it signs everyone out |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | first run only | `admin@example.com`, `<strong password>` | Creates the first administrator if no account has that email. Never changes an existing account. Remove the password afterwards |
| `ADMIN_FULL_NAME` | no | `Administrator` | Name of that administrator |
| `JWT_TTL` | no | `PT2H` | How long a login lasts (ISO-8601 duration) |
| `AUTH_COOKIE_SECURE` | no | `false` | Set `true` when the site runs on HTTPS, so the login cookie is only sent over HTTPS |
| `CORS_ORIGINS` | no | `http://localhost:5173` | Frontend origins allowed to call the API from another origin |
| `SERVER_PORT`, `MODEL_PATH` | no | `8081`, `../ML-Service/weights/model.onnx` | Port and model file |

Spring Boot 4 reads the URI as `spring.mongodb.uri=${MONGODB_URI}` (Boot 3 called it `spring.data.mongodb.uri`).

### Database `breast_cancer`

| Collection | Contents | Indexes |
|---|---|---|
| `users` | `fullName`, `email` (lower-cased), `passwordHash` (BCrypt), `role`, `createdAt`, `updatedAt`, `passwordChangedAt`, and `profile: { phone, dateOfBirth, gender, address, specialization, hospital }` (all optional; accounts made before profiles simply lack them, no migration needed) | `email_unique` (unique) |
| `predictions` | each scan's metadata and report, plus `ownerId` and `sharedWithDoctorIds` | `owner_createdAt`, `sharedWithDoctorIds` |
| `fs.files`, `fs.chunks` | the uploaded images (GridFS) | created by GridFS |
| `revoked_tokens` | logged-out tokens (`_id` = token id) until they would have expired | `expiresAt_ttl` (TTL: MongoDB deletes expired entries) |
| `contact_messages` | Contact form messages, with `status` NEW/READ (older messages without it count as NEW) | `status_createdAt` |
| `site_settings` | one document (`_id: "site"`): footer contacts, social links, Home video, announcement, who saved it last | — |

`users` also holds `disabled` and `lastLoginAt`, and `predictions` / `users` get `createdAt` / `role` indexes for the admin overview. All are created at startup; older documents need no migration.

Scans and their reports are one `predictions` document each, so there are no separate `scans` / `reports` collections.

**Older predictions.** Records saved before accounts existed have no `ownerId`. They are kept, but nobody can open them through the site (the backend logs how many there are at startup). To give one to a patient, set its owner in Atlas (*Browse Collections* or `mongosh`):
```js
db.predictions.updateOne({ _id: ObjectId("<prediction id>") }, { $set: { ownerId: "<user id from the users collection>" } })
```

## 2. The model (ONNX)

`ML-Service/weights/model.onnx` is what the backend loads and runs **on the server's CPU** with ONNX Runtime. Visitors' devices only upload the image, so no one needs a GPU to use the site. Training is a one-time developer step done on Kaggle, never on the machine that runs the website.

The model file carries its own test results (accuracy, precision, recall, specificity, F1, ROC-AUC, overall and for CC and MLO views). The website shows exactly those numbers (`GET /api/model`, the "How accurate is this model?" panel on `/prediction`). A model without them runs in **demo mode**: results are marked meaningless and no accuracy is shown.

### What runs on the server (CPU, about 6 seconds per image)

1. **Colour check**: colour photos are refused (mammograms are grayscale).
2. **Mammogram check** (`weights/gate/gate.onnx`, MobileNetV3, `ML-Service/train_gate.py`): refuses anything that is not a mammogram, such as grey cartoons, photos, drawings, chest or dental X-rays, ultrasound and documents. Without this file every upload is refused.
3. **Cancer estimate**, three models in two stages, each joined by logistic regression on logits:
   - the ResNet50 fine-tuned on CBIS-DDSM below (`weights/model.onnx`, 0.2 s) and net0 of [ianpan/mammoscreen](https://huggingface.co/ianpan/mammoscreen) (Apache-2.0) with [ianpan/mammo-crop](https://huggingface.co/ianpan/mammo-crop) (`weights/mammoscreen/`, 1.7 s), combined as in `weights/ensemble/ensemble.json` (`ML-Service/compare_ensemble.py`);
   - that estimate joined with fold 0 of the **RSNA 2023 Screening Mammography Breast Cancer Detection 1st-place model** ([dangnh0611](https://github.com/dangnh0611/kaggle_rsna_breast_cancer), MIT; ConvNeXt-small at 2048 x 1024, `weights/firstplace/firstplace_fold0.onnx`, 3.8 s), as in `weights/final/final.json` (`ML-Service/calibrate_final.py`).
4. **Result**: *Malignant* when the final estimate is 50% or more (the larger of the two percentages), otherwise *Benign*; both percentages are shown.

Each stage falls back when a file is missing: without the 1st-place model the two-model combination is used, without mammoscreen the ResNet alone.

**Why INbreast decides**: the 1st-place model was trained on RSNA, VinDr-Mammo, MiniDDSM (which contains CBIS-DDSM), CMMD, CDD-CESM and BMCD, so CBIS-DDSM and VinDr can no longer test it fairly. INbreast (Portugal, digital, 410 images, 108 patients) is in no model's training data. The final combination was fitted and tested on INbreast by patient-split cross-validation (fitted on half the patients, scored on the other half, 20 random splits), so every reported score comes from a fit that never saw that patient.

| INbreast, unseen patients | Current two-model estimate | 1st-place fold 0 alone | **Final (installed)** |
|---|---|---|---|
| ROC-AUC, BI-RADS 5-6 vs 1-3 | 0.916 | 0.925 | **0.948** (over 20 splits 0.911-0.948) |
| ROC-AUC, BI-RADS 4-6 vs 1-3 | 0.844 | 0.847 | **0.871** |
| At 50%: accuracy / cancers found / benign right (5-6 vs 1-3) | 89.1% / 78.9% / 91.0% | - | **93.7% / 68.4% / 98.4%** |

INbreast is small, so the intervals are wide; its labels are BI-RADS (6 = biopsy-proven). The earlier two-model results on CBIS-DDSM and VinDr are below.

**Earlier two-model results** (ROC-AUC; sensitivity / specificity at its threshold):

| Test set | ResNet alone | mammoscreen net0 alone | **Combined (installed)** |
|---|---|---|---|
| CBIS-DDSM test, 595 images, biopsy-proven malignant vs benign | 0.760 | 0.681 | **0.786** (62% / 82%) |
| VinDr-Mammo test, 2,218 images, BI-RADS 4-5 vs 1-3 | 0.652 | 0.637 | **0.712** (56% / 76%) |
| VinDr-Mammo test, BI-RADS 4-5 vs normal (BI-RADS 1) | 0.636 | 0.684 | **0.710** (56% / 77%) |

Mammogram check, on images it never saw while training: grey cartoons 99.9% refused, sketches 99.3% refused; mammograms from an unseen source (MIAS) 89.8% accepted, so a few real mammograms from new machines can be refused. The final check was trained on all categories.

Other models tried and not used: the original Hugging Face classifier (ROC-AUC 0.55, found 2% of cancers), mammoscreen's full 3-net ensemble (no better than net0 here, 5 s per image), `edziocodes/medgemma-breast-cancer` (a 4-billion-parameter chat model trained on 256 px slices; about 10 GB, minutes per image on a CPU).

> These numbers are far from perfect: the estimate misses cancers and raises false alarms. VinDr labels are radiologists' BI-RADS, not biopsies. Research and education only.

**Files to deploy** (all git-ignored, about 390 MB): `weights/model.onnx`, `weights/mammoscreen/crop.onnx`, `weights/mammoscreen/net0.onnx`, `weights/ensemble/ensemble.json`, `weights/firstplace/firstplace_fold0.onnx`, `weights/final/final.json`, `weights/gate/gate.onnx`, `weights/gate/gate_metrics.json`. Paths can be changed with `MODEL_PATH`, `MAMMOSCREEN_DIR`, `ENSEMBLE_PATH`, `FIRSTPLACE_PATH`, `FINAL_PATH` and `GATE_PATH`. Kaggle notebooks that rebuild them: `kaggle_train.ipynb` (ResNet), `kaggle_mammoscreen.ipynb` (mammoscreen export), `kaggle_gate.ipynb` (mammogram check); `compare_ensemble.py` produced the combination.

### The model and why it is retrained

- Starting point: the ResNet50 mammography classifier from [umairinayat/medical-models](https://huggingface.co/umairinayat/medical-models) (MIT). Its card reports 99.6% accuracy, but that was measured on a random split of heavily augmented lesion crops (about 72 copies of each INbreast image), where copies of the same lesion are in training and test. On real full mammograms from unseen patients it scored **59% accuracy, ROC-AUC 0.55, and caught 2% of cancers** (5 of 244; `ML-Service/weights/hf_baseline_metrics.json`). Those published numbers are therefore not used anywhere.
- It is fine-tuned on **CBIS-DDSM full mammograms, both CC and MLO views** (TCIA, CC BY 3.0; 3,086 images via Hugging Face `dbaek111/CBIS-DDSM_1024`, labels from `FLARE25-Agent-Xray/CBIS-DDSM`). Splits are **by patient**: test = the official CBIS-DDSM test set minus patients also in training; validation = 15% of training patients. The best epoch and the decision threshold are chosen on validation; the test set is used once, at the end.
- Classes: benign / malignant (CBIS-DDSM has no normal images). The result is flagged as uncertain when the probability is within 10 points of the threshold.
- The backend prepares images exactly like training (grayscale, Pillow-identical bilinear resize to 512 x 512; checked by `PreprocessingTest`) and refuses colour photos, which are not mammograms.

### Train on Kaggle (free GPU, about 30-60 minutes)

1. Sign in at https://www.kaggle.com, verify your phone number once (needed for internet access in notebooks).
2. **Create → New Notebook → File → Import Notebook**, and upload `ML-Service/kaggle_train.ipynb`.
3. In the notebook's right-hand panel: **Accelerator → GPU T4 x2**, **Internet → On**.
4. **Run All**. The notebook downloads the starting model and the data (checking their SHA-256), splits by patient, trains, tests, exports `model.onnx`, and checks that ONNX gives the same answers as PyTorch. The last cell prints the test results.
5. From the **Output** panel download `breastcare_model.zip`, unzip it into `ML-Service/weights/` (replacing `model.onnx`), and restart the backend. The startup log shows the model version; `/prediction` now shows its test results.

To deploy, copy the files listed above to the server (see **Files to deploy**).

If you change any script in `ML-Service`, run `python make_kaggle_notebook.py` so the notebook carries the new version. The scripts can also run on any CUDA GPU machine directly: `python download_pretrained.py`, `prepare_cbis.py`, `train.py`, `evaluate.py`, `export_onnx.py` (on a GTX 16xx card add `--no-amp` to `train.py`; its fp16 gives NaN).

Optional check that the backend gives the same probability as Python for a model (`portrait.png` is in `Backend/src/test/resources/preprocessing`):
```powershell
$env:PARITY_MODEL="..\ML-Service\weights\model.onnx"; $env:PARITY_EXPECTED="<P(malignant) from Python>"
.\mvnw.cmd test -Dtest=ModelParityCheck
```

> Limits: CBIS-DDSM test images all have a biopsy-checked finding, so performance on routine screening (mostly normal images) is not measured, and real-world results can be lower. Research and education only.

## 3. Run the website

**Single server (how users use it):**

```powershell
cd Frontend
npm install
npm run build          # builds the site into Backend/src/main/resources/static

cd ..\Backend
.\mvnw.cmd spring-boot:run
```

Open **http://localhost:8081**.

**Development with hot reload:** run the backend as above, then run `npm run dev` in `Frontend` and open http://localhost:5173. Vite forwards `/api` to port 8081, so the login cookie works the same way.

Port 8080 is not used, because Apache/XAMPP runs there on this machine. To change the port, set `SERVER_PORT` in `.env`.

## Accounts and roles (F02)

| Role | Gets an account by | Can |
|---|---|---|
| **PATIENT** | signing up at `/register` | analyse mammograms; see, delete and share **only their own** reports (`/dashboard`, `/prediction`, `/history`, `/reports/{id}`) |
| **DOCTOR** | an administrator creating it, or changing a user's role | see the reports patients have **shared with them** (`/doctor`, `/reports/{id}`); cannot upload or delete |
| **ADMIN** | `ADMIN_EMAIL`/`ADMIN_PASSWORD` on first start, or another admin | the Admin Console at `/admin`: site overview and system status, user management (create, change role, disable/enable, last login), the Contact inbox, and website settings (footer contacts, social links, Home video, announcement banner). **Cannot read individual medical records**, only totals. Cannot change their own role or disable themselves, so there is always an admin |

Public sign-up always creates a PATIENT; a `role` sent in the request is ignored.

### How sign-in works

- **Passwords** are hashed with BCrypt (cost 12). 8–72 characters with upper-case, lower-case and a number; the same rules are checked in the browser and the backend.
- **Login** issues a JWT signed with HS256 (`JWT_SECRET`), holding the user id, a random token id (`jti`), the issuer and an expiry. It is sent as the `bc_auth` cookie: `HttpOnly` (page scripts cannot read it), `SameSite=Strict`, `Path=/api`, and `Secure` when `AUTH_COOKIE_SECURE=true`. The token is never in a response body or `localStorage`.
- **Every API request** verifies the signature, issuer, expiry and that the token was not logged out, then loads the user from MongoDB. The role therefore comes from the database, so a role change or deleted account applies on the next request. Bad, tampered, expired or revoked tokens get `401`; a wrong role gets `403`.
- **CSRF**: because the browser sends cookies by itself, every POST/PATCH/DELETE also needs a CSRF token (Spring Security's double-submit cookie: readable `XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header). The frontend handles this automatically (`services/http.js`).
- **Logout** stores the token id in `revoked_tokens`, so that token stops working immediately, even a copied one, and removes the cookie. Entries delete themselves when the token would have expired.
- **Records**: the owner is always the signed-in user from the verified token, never an id sent by the browser. Every lookup filters by owner (patients) or share list (doctors) in the MongoDB query itself. A record the user may not see answers `404`, the same as a missing one, so ids cannot be probed.
- The frontend's route guards only decide which page to show; the backend enforces every rule itself.

## API

Errors are `application/problem+json` with a readable `detail`; form errors also carry `errors: { field: message }`.
All POST/PATCH/DELETE requests need the CSRF header described above.

| Method | Path | Who | Description |
|---|---|---|---|
| GET | `/api/auth/csrf` | anyone | sets the `XSRF-TOKEN` cookie → `204` |
| POST | `/api/auth/register` | anyone | `{fullName, email, password}` → `201` user; `400` invalid fields; `409` email already used |
| POST | `/api/auth/login` | anyone | `{email, password}` → `200` user + `bc_auth` cookie; `401` "Invalid email or password." |
| POST | `/api/auth/logout` | anyone | revokes the token, clears the cookie → `204` |
| GET | `/api/auth/me` | signed in | `{id, fullName, email, role, createdAt}`; `401` when not signed in |
| GET | `/api/profile` | signed in | own profile: `{id, fullName, email, role, createdAt, updatedAt, phone, dateOfBirth, gender, address, specialization, hospital}` |
| PUT | `/api/profile` | signed in | `{fullName, phone, dateOfBirth (YYYY-MM-DD), gender (FEMALE/MALE/OTHER/PREFER_NOT_TO_SAY), address, specialization, hospital}`; blank clears a field; specialization/hospital are kept for doctors only; role/email/password cannot change here |
| PUT | `/api/profile/email` | signed in | `{email, currentPassword}` → profile; `400` wrong password; `409` email in use |
| PUT | `/api/profile/password` | signed in | `{currentPassword, newPassword}` → profile + new login cookie; every other login of this account stops working |
| POST | `/api/predictions` | PATIENT | multipart field `image` (PNG or JPEG, ≤ 50 MB, checked by content, grayscale), `view` (`CC` or `MLO`, required) and optional `uploadId` → `201` prediction owned by the caller; the same `uploadId` again returns the first record instead of a second one. `400` not a PNG/JPEG or unreadable; `413` too large; `503` model or database unavailable |
| GET | `/api/predictions` | PATIENT | own history, newest first |
| GET | `/api/model` | anyone | running model: `{available, version, trained, threshold, uncertainMargin, imageHeight, imageWidth, metrics}`; `metrics` is the held-out test report (null in demo mode) |
| GET | `/api/predictions/{id}` | PATIENT (own), DOCTOR (shared) | one prediction; `404` if missing or not yours |
| GET | `/api/predictions/{id}/image` | PATIENT (own), DOCTOR (shared) | the stored image |
| DELETE | `/api/predictions/{id}` | PATIENT (own) | delete the record and its image |
| GET | `/api/predictions/{id}/shares` | PATIENT (own) | doctors it is shared with `[{id, fullName}]` |
| POST | `/api/predictions/{id}/shares` | PATIENT (own) | `{doctorId}` → updated share list |
| DELETE | `/api/predictions/{id}/shares/{doctorId}` | PATIENT (own) | stop sharing → `204` |
| GET | `/api/predictions/shared` | DOCTOR | `[{report, patient: {id, fullName}}]` shared with the caller |
| GET | `/api/doctors` | PATIENT | doctors to share with `[{id, fullName}]` |
| GET | `/api/site-settings` | anyone | website settings `{configured, contactPhone, contactEmail, contactAddress, facebookUrl, youtubeUrl, linkedinUrl, twitterUrl, heroVideoUrl, announcementText, announcementEnabled}`; `configured: false` until an admin saves them (the site then uses `Frontend/src/config/siteConfig.js`) |
| GET | `/api/admin/overview` | ADMIN | site totals (users by role, analyses, benign/malignant, shared, messages), uploads per day for 14 days, system status. Totals only, no patient data |
| GET | `/api/admin/site-settings` | ADMIN | as `/api/site-settings`, plus `updatedAt` and `updatedBy` |
| PUT | `/api/admin/site-settings` | ADMIN | save all website settings; links must be `http(s)://`, the video a YouTube link |
| GET | `/api/admin/messages?status=NEW\|READ` | ADMIN | Contact form messages, newest first (no `status` = all) |
| PATCH | `/api/admin/messages/{id}` | ADMIN | `{status: "NEW" \| "READ"}` |
| DELETE | `/api/admin/messages/{id}` | ADMIN | delete a message → `204` |
| PATCH | `/api/admin/users/{id}/status` | ADMIN | `{disabled: true \| false}`; a disabled account cannot log in and its logins stop working; not allowed on your own account |
| GET | `/api/admin/users` | ADMIN | all users with `disabled` and `lastLoginAt` (no password hashes) |
| POST | `/api/admin/users` | ADMIN | `{fullName, email, password, role}` → `201` user |
| PATCH | `/api/admin/users/{id}/role` | ADMIN | `{role}` → user; `400` for your own account |
| POST | `/api/contact` | anyone | JSON `{name, email, subject, message}` → `201 {id, message, receivedAt}`; `400` with per-field `errors` when invalid. Stored in the `contact_messages` collection |
| GET | `/actuator/health` | anyone | health check (other actuator endpoints: ADMIN) |

Prediction JSON is the same as before, plus `ownerId`, `view` (CC/MLO), `threshold` (the model's decision threshold) and `lowConfidence`. Older records have them null/false.

## Mammogram upload (F05)

`/prediction` (patients) is the upload page: [`views/PredictionView.vue`](Frontend/src/views/PredictionView.vue) with [`components/prediction/MammogramUpload.vue`](Frontend/src/components/prediction/MammogramUpload.vue) and [`UploadPrivacyNotice.vue`](Frontend/src/components/prediction/UploadPrivacyNotice.vue).

- **View**: the patient chooses CC (craniocaudal) or MLO (mediolateral oblique); it is required, stored with the report and shown with the result.
- **Choosing a file**: drag and drop, or *Browse Files* (keyboard accessible). One image at a time; a preview, the file name, format and size are shown, with *Change image* and *Remove*.
- **Accepted files**: PNG and JPEG (`.png`, `.jpg`, `.jpeg`) up to 50 MB. DICOM is not supported, because the backend reads images with Java ImageIO. The limits live in [`Frontend/src/prediction/mammogramFile.js`](Frontend/src/prediction/mammogramFile.js) and, on the server, `spring.servlet.multipart.*` in `application.properties` plus `PredictionService`; change both together.
- **Checks in the browser**: extension, the browser's MIME type, size, empty files, and the file's first bytes (PNG/JPEG signature), so a renamed document is caught before uploading. The backend checks everything again on its own and stores the type found in the file's bytes, never the one the browser sent.
- **Consent**: the privacy notice describes what the backend really does (image analysed on this server, saved with the result to the patient's account, visible to the patient and the doctors they share with, kept until the patient deletes the report). *Upload Image* stays disabled until a valid file is chosen and the consent box is ticked. Nothing is uploaded before that, and nothing is put in browser storage.
- **Upload**: one request stores the image and runs the model, so the page shows real upload progress (percentage) and then an "Analysing…" step until the backend answers. Success and the result are shown only from the backend's response.
- **Errors and retry**: network failures, timeouts and `5xx` show *Retry upload*, which keeps the chosen file and sends the same `uploadId`, so a retry after a dropped connection cannot save the image twice. Rejected files (`400`, `413`) ask for another image; an expired login (`401`) goes to the login page as everywhere else.

## AI result page and heatmap viewer (F09)

`/reports/{id}` (the patient who owns the report, or a doctor it is shared with) is [`views/ReportView.vue`](Frontend/src/views/ReportView.vue) with the components in [`components/report/`](Frontend/src/components/report/).

- **Score and risk category**: [`ResultCard.vue`](Frontend/src/components/prediction/ResultCard.vue) shows Benign/Malignant, both percentages and an *AI risk category*: Low or High from the verdict, Intermediate when the model was not confident (`lowConfidence`, or the older `uncertain` label). The rule is `riskCategory()` in [`prediction/verdict.js`](Frontend/src/prediction/verdict.js).
- **Scan and heatmap**: [`ScanViewer.vue`](Frontend/src/components/report/ScanViewer.vue) shows the original image and switches between *Original* and *Heatmap* (with an opacity slider and a colour legend). Zoom with the buttons, Ctrl + scroll (or trackpad pinch), double-click or the `+` `-` `0` keys; drag or use the arrow keys to move when zoomed in.
- **Where the heatmap comes from**: the ResNet50 ends in average pooling and one linear layer, so applying that layer to each cell of the last feature map gives each region's share of the score (a class activation map). [`ML-Service/heatmap_output.py`](ML-Service/heatmap_output.py) reads `weights/model.onnx` (it never changes it) and writes a copy with that extra output, `weights/heatmap.onnx` (16 x 16 cells for a 512 x 512 input). It checks that the map's mean gives back the model's own probabilities. Run it once, and again whenever `model.onnx` is replaced: `cd ML-Service; .venv\Scripts\python heatmap_output.py`. Without `heatmap.onnx` the report says a heatmap is not available.
- **The score is not affected**: training, `export_onnx.py`, `model.onnx` and the analysis (`OnnxPredictor`, `CancerModel`) are unchanged. The backend runs `heatmap.onnx` ([`HeatmapModel.java`](Backend/src/main/java/com/cancerweb/backend/service/HeatmapModel.java), path `ml.heatmap.path`) only to draw the picture.
- **Heatmap API**: `GET /api/predictions/{id}/heatmap` (same access rules as the report) returns `{ rows, cols, values, modelVersion }`, where each value is that region's malignant minus benign logit. It is made from the stored image the first time a report is opened, then kept with the report. Answers `204` when the server has no `heatmap.onnx`.
- **Limits**: the heatmap explains the ResNet50 only. The other models in the combined score (mammoscreen, the RSNA 1st-place model) give no map. Colours are relative to the strongest region in that image, so a benign result has coloured areas too. It shows what the AI looked at, not where a tumour is. On some images the strongest areas are outside the breast (background, labels).
- **Status**: [`ReportStatus.vue`](Frontend/src/components/report/ReportStatus.vue) shows the scan status (analysis complete, low confidence, or demo model) and the doctor review status: *Not shared with a doctor*, *Awaiting doctor review* (with the doctors' names) for the owner, or *Shared with you for review* for a doctor. The site has no "reviewed" step yet, so a report cannot be marked as reviewed.
- **Disclaimer**: [`MedicalDisclaimer.vue`](Frontend/src/components/report/MedicalDisclaimer.vue) stays stuck to the bottom of the screen on the report page, including when the report cannot be opened, and cannot be closed.

## Pages

Public: `/` Home · `/about` · `/how-it-works` · `/faq` · `/contact` · `/login` · `/register`, plus Not Found and Access Denied pages.
Signed in: `/profile` (everyone) · `/dashboard`, `/prediction`, `/history` (patient) · `/doctor` (doctor) · `/admin` (administrator) · `/reports/{id}` (owner, or a doctor it is shared with).
Paths live in `Frontend/src/config/appRoutes.js` and `Frontend/src/router/index.js`.

## Tests

```powershell
cd Backend
.\mvnw.cmd test        # auth, tokens, roles, record ownership, MongoDB setup, prediction flow

cd ..\Frontend
npm test               # validation, route guards, session state, API client, login/register forms, mammogram upload, result page
```

Backend tests run against a temporary local MongoDB that Flapdoodle downloads once (cached in `~/.embedmongo`). They never connect to Atlas, and refuse to delete data unless they are on the `breast_cancer_test` database. No Docker is needed.

## Security notes and limits

- Login attempts are not rate-limited yet. Before going public, add rate limiting (for example at a reverse proxy) to slow down password guessing.
- There are no refresh tokens: when a login expires (`JWT_TTL`, 2 hours by default), the user logs in again. The site sends them to the login page and back to where they were.
- In production, serve the site over HTTPS and set `AUTH_COOKIE_SECURE=true`. Keep `JWT_SECRET` and the Atlas password only in the server's environment, and narrow the Atlas Network Access list to the server's IP.
- The frontend and API are meant to share one site (Spring Boot serves both, or the Vite proxy in development). A frontend on a different domain would need cookie and CSRF changes.
- There is no password reset or email verification yet.
