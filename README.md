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
| `contact_messages` | Contact form messages | — |

Scans and their reports are one `predictions` document each, so there are no separate `scans` / `reports` collections.

**Older predictions.** Records saved before accounts existed have no `ownerId`. They are kept, but nobody can open them through the site (the backend logs how many there are at startup). To give one to a patient, set its owner in Atlas (*Browse Collections* or `mongosh`):
```js
db.predictions.updateOne({ _id: ObjectId("<prediction id>") }, { $set: { ownerId: "<user id from the users collection>" } })
```

## 2. The model (ONNX)

`ML-Service/weights/model.onnx` is what the backend loads. A demo version is already exported. It is untrained, so its numbers are random, and the website shows a "Demo mode" warning.

To get real predictions, train on mammograms (for example CBIS-DDSM). Use a GPU, such as Google Colab or Kaggle:

```powershell
cd ML-Service
python -m venv .venv
.\.venv\Scripts\activate
pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu   # or the CUDA build on a GPU
pip install -r requirements.txt

# data/train/{benign,malignant}/*.png and data/val/{benign,malignant}/*.png
python train.py --data data --epochs 15     # -> weights/model.pt
python export_onnx.py                       # -> weights/model.onnx
```

Restart the backend to load the new model. If you train on Colab, copy `model.onnx` into `ML-Service/weights/`.

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
| **ADMIN** | `ADMIN_EMAIL`/`ADMIN_PASSWORD` on first start, or another admin | list users, create accounts with any role, change roles (`/admin`). **Cannot read medical records.** Cannot change their own role, so there is always an admin |

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
| POST | `/api/predictions` | PATIENT | multipart field `image` (PNG or JPG, ≤ 50 MB) → prediction owned by the caller |
| GET | `/api/predictions` | PATIENT | own history, newest first |
| GET | `/api/predictions/{id}` | PATIENT (own), DOCTOR (shared) | one prediction; `404` if missing or not yours |
| GET | `/api/predictions/{id}/image` | PATIENT (own), DOCTOR (shared) | the stored image |
| DELETE | `/api/predictions/{id}` | PATIENT (own) | delete the record and its image |
| GET | `/api/predictions/{id}/shares` | PATIENT (own) | doctors it is shared with `[{id, fullName}]` |
| POST | `/api/predictions/{id}/shares` | PATIENT (own) | `{doctorId}` → updated share list |
| DELETE | `/api/predictions/{id}/shares/{doctorId}` | PATIENT (own) | stop sharing → `204` |
| GET | `/api/predictions/shared` | DOCTOR | `[{report, patient: {id, fullName}}]` shared with the caller |
| GET | `/api/doctors` | PATIENT | doctors to share with `[{id, fullName}]` |
| GET | `/api/admin/users` | ADMIN | all users (no password hashes) |
| POST | `/api/admin/users` | ADMIN | `{fullName, email, password, role}` → `201` user |
| PATCH | `/api/admin/users/{id}/role` | ADMIN | `{role}` → user; `400` for your own account |
| POST | `/api/contact` | anyone | JSON `{name, email, subject, message}` → `201 {id, message, receivedAt}`; `400` with per-field `errors` when invalid. Stored in the `contact_messages` collection |
| GET | `/actuator/health` | anyone | health check (other actuator endpoints: ADMIN) |

Prediction JSON is the same as before, plus `ownerId`.

## Pages

Public: `/` Home · `/about` · `/how-it-works` · `/faq` · `/contact` · `/login` · `/register`, plus Not Found and Access Denied pages.
Signed in: `/profile` (everyone) · `/dashboard`, `/prediction`, `/history` (patient) · `/doctor` (doctor) · `/admin` (administrator) · `/reports/{id}` (owner, or a doctor it is shared with).
Paths live in `Frontend/src/config/appRoutes.js` and `Frontend/src/router/index.js`.

## Tests

```powershell
cd Backend
.\mvnw.cmd test        # auth, tokens, roles, record ownership, MongoDB setup, prediction flow

cd ..\Frontend
npm test               # validation, route guards, session state, API client, login/register forms
```

Backend tests run against a temporary local MongoDB that Flapdoodle downloads once (cached in `~/.embedmongo`). They never connect to Atlas, and refuse to delete data unless they are on the `breast_cancer_test` database. No Docker is needed.

## Security notes and limits

- Login attempts are not rate-limited yet. Before going public, add rate limiting (for example at a reverse proxy) to slow down password guessing.
- There are no refresh tokens: when a login expires (`JWT_TTL`, 2 hours by default), the user logs in again. The site sends them to the login page and back to where they were.
- In production, serve the site over HTTPS and set `AUTH_COOKIE_SECURE=true`. Keep `JWT_SECRET` and the Atlas password only in the server's environment, and narrow the Atlas Network Access list to the server's IP.
- The frontend and API are meant to share one site (Spring Boot serves both, or the Vite proxy in development). A frontend on a different domain would need cookie and CSRF changes.
- There is no password reset or email verification yet.
