# Deploying BreastCare (live demo)

This guide puts the website online so that it works without your laptop. Follow the steps in order.

## 1. What the project actually is (checked in the code)

| Part | What it is | Where it goes |
|---|---|---|
| Frontend | Vue 3 + Vite (`Frontend/`) | **Vercel** (free) |
| Backend + AI | Spring Boot 4, Java 21, Maven (`Backend/`). The AI runs **inside** the backend with ONNX Runtime | **Render** (Docker) |
| AI model files | 9 files, about 460 MB (`ML-Service/weights/`) | **Private Hugging Face model repo** (free) |
| Database | MongoDB (Atlas). Scans and images are stored in MongoDB (GridFS), not on the server disk | **MongoDB Atlas** (free M0) |

There is **no separate Python ML service** to deploy. `ML-Service/` only holds the training and export scripts. They
produced the ONNX files, and the Java backend runs those files directly (`OnnxPredictor`, `MammoscreenNet`,
`FirstPlaceNet`, `MammogramGate`, `HeatmapModel`). Moving the AI into a new Python service would mean rewriting the image
preprocessing in Python, which could change the results. So the AI stays where it is.

**The classes are only Benign and Malignant.** Training labels are 0 = benign and 1 = malignant
(`ML-Service/prepare_cbis.py`). The backend reads output index 1 as the malignant probability. Nothing about the
models, the preprocessing or the decision threshold changes for deployment.

How a request flows after deployment:

```
Browser → https://<your-site>.vercel.app
          ├─ pages, JS, CSS      (served by Vercel)
          └─ /api/...            (Vercel forwards it to Render)
                 → https://breastcare-backend.onrender.com  (Spring Boot + ONNX models)
                        → MongoDB Atlas  (accounts, scans, images, reports)
```

**Why `/api` goes through Vercel instead of `VITE_API_URL`.** Login uses an HttpOnly cookie, plus a CSRF token cookie
that the page's JavaScript reads. If the page called `onrender.com` directly, those would be cross-site cookies. Browsers
block cross-site cookies, and JavaScript on `vercel.app` cannot read a cookie set by `onrender.com`, so login would
fail. With the forwarding rule in `Frontend/vercel.json`, the browser only ever talks to one domain. Leave
`VITE_API_URL` **unset** on Vercel.

## 2. Resources and cost

Measured on this laptop: the backend uses about 0.7 GB right after it starts. After it analyses a mammogram it uses
**about 3 GB**, and that stays the same even with the Java heap capped at 384 MB. The memory goes to the five ONNX
models working on full-size mammograms, not to Java. No GPU is needed: one analysis took about 6 s on this laptop's CPU
and will take longer on 2 CPUs.

| Service | Plan | Cost |
|---|---|---|
| Vercel | Hobby | free |
| MongoDB Atlas | M0 | free (512 MB of data, so delete old test scans now and then) |
| Hugging Face model repo | free account, private repo | free |
| Render | **Free** (512 MB, `plan: free` in `render.yaml`) | free. Runs everything **except** the mammogram analysis |

**Current setup: free plan, no AI on Render.** Without the models the backend uses about 220 MB (measured), so it fits
the free plan. Analyses answer "not available" (503); everything else works. To run the AI on Render, change `plan:` to
`2c-4g` (paid, about US$85 per month) and add `HF_MODEL_REPO` and `HF_TOKEN`. On the free plan the service sleeps after
15 minutes without visitors, and the first request then takes about a minute.

Local storage needed: nothing new. The build runs in the cloud, and you do not need Docker on the laptop.

## 3. Step by step

### Step 1 — Upload the model files to Hugging Face (once, from this laptop)

1. Create a free account at https://huggingface.co/join.
2. Go to https://huggingface.co/settings/tokens and create a **Write** token. Keep it to yourself.
3. In PowerShell:
   ```powershell
   cd D:\desktop\Cancer_web\ML-Service
   .\.venv\Scripts\python -m pip install huggingface_hub
   .\.venv\Scripts\hf auth login            # paste the Write token
   .\.venv\Scripts\python upload_weights.py <your-hf-username>/breastcare-models
   ```
   This uploads only the 9 files the backend needs (about 460 MB) to a **private** repo.
4. Create a second token at https://huggingface.co/settings/tokens. Make it **Read** only. Render will use it.

### Step 2 — MongoDB Atlas

You already have an Atlas cluster (`MONGODB_URI` in `Backend/.env`).

1. Atlas → **Network Access** → **Add IP Address** → `0.0.0.0/0`. Render has no fixed IP address, so this is needed.
   The database user's strong password is what protects the data.
2. Atlas → **Database Access**: make sure the database user has a long random password.
3. Copy the connection string (Atlas → Connect → Drivers), with the database name after `.net/`:
   `mongodb+srv://<user>:<password>@<cluster>.mongodb.net/breast_cancer?retryWrites=true&w=majority`

The backend creates its collections and indexes itself. Nothing is dropped or migrated.

### Step 3 — Push the code to GitHub

The deployment files are `render.yaml`, `Frontend/vercel.json`, `Backend/scripts/docker-entrypoint.sh`,
`ML-Service/upload_weights.py` and this guide. Push them to `main` of
`https://github.com/ragibKhandoker/Breast_Cancer_Web_App`. `.env` files are git-ignored and must never be pushed.

### Step 4 — Backend on Render

1. Create an account at https://render.com and sign in with GitHub.
2. **New → Blueprint** → choose the `Breast_Cancer_Web_App` repo. Render reads `render.yaml`.
3. Fill in the values it asks for:

   | Key | Value |
   |---|---|
   | `MONGODB_URI` | the Atlas string from step 2 |
   | `ADMIN_EMAIL` / `ADMIN_PASSWORD` | the first admin account (password: 8–72 characters with upper-case, lower-case and a number) |
   | `GEMINI_API_KEY` | optional; leave empty and the chatbot answers from the Knowledge Hub |

   `JWT_SECRET` is generated by Render. `PORT`, `AUTH_COOKIE_SECURE`, `CORS_ORIGINS` (`https://breastcare-fawn.vercel.app`),
   `JAVA_TOOL_OPTIONS` and `BACKUP_SCHEDULE` are already set in `render.yaml`.
4. **Apply**. The first build takes about 10 minutes. Then the service downloads the models and starts.
   In **Logs** you should see `Starting without mammogram analysis` followed by the Spring Boot start-up
   (or `Downloading model files ...` once `HF_MODEL_REPO` is set).
5. The backend address is shown at the top, e.g. `https://breastcare-backend.onrender.com`.
   **If it is different**, change the `destination` of the `/api` rule in `Frontend/vercel.json` and push again.
6. Check it: open `https://breastcare-backend.onrender.com/actuator/health`. It should show `{"status":"UP"}`.

Build and start commands (already in the Dockerfile; Render runs them):
- build: `mvn -B -Dmaven.test.skip=true package`
- start: `/app/docker-entrypoint.sh`, which fetches the models and runs `java -jar /app/app.jar`

### Step 5 — Frontend on Vercel

1. Create an account at https://vercel.com and sign in with GitHub.
2. **Add New → Project** → import `Breast_Cancer_Web_App`.
3. **Root Directory**: `Frontend`. Vercel detects Vite, and `vercel.json` sets:
   install `npm ci`, build `npm run build`, output `dist`.
4. Environment variables: **none**. Do not set `VITE_API_URL` (see section 1).
5. **Deploy**. You get an address like `https://breast-cancer-web-app.vercel.app`.
6. Back in Render → the service → **Environment** → set `CORS_ORIGINS` to exactly that address, with no `/` at the end.
   Save; Render restarts.

## 4. Environment variables (backend)

| Variable | Required | Notes |
|---|---|---|
| `MONGODB_URI` | yes | Atlas connection string |
| `JWT_SECRET` | yes | at least 32 characters (Render generates it) |
| `AUTH_COOKIE_SECURE` | yes | `true` on HTTPS |
| `CORS_ORIGINS` | yes | the Vercel address |
| `HF_MODEL_REPO`, `HF_TOKEN` | yes on Render | where the model files come from |
| `PORT` | yes on Render | `8080` |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_FULL_NAME` | first start | creates the first administrator |
| `GEMINI_API_KEY` | no | the chatbot works without it |
| `BACKUP_SCHEDULE` | no | `-` (off). Render's disk is temporary |
| `BACKUP_ENCRYPTION_KEY` | no | only if you turn backups on with somewhere persistent to keep them |

Frontend: no variables.

## 5. Security checklist

- [ ] HTTPS everywhere: Vercel and Render both give certificates automatically.
- [ ] No secrets in Git: `.env` is git-ignored; secrets live only in the Render dashboard.
- [ ] Hugging Face repo is **private**; Render has only a **read** token.
- [ ] Login cookie is HttpOnly and Secure (`AUTH_COOKIE_SECURE=true`), with CSRF protection on every change.
- [ ] CORS allows only the Vercel address.
- [ ] Uploads: PNG/JPEG only, max 50 MB, and the mammogram gate model refuses images that are not mammograms.
- [ ] Images are stored privately in MongoDB (GridFS). Only the patient, and doctors the patient chooses, can open them.
- [ ] Patient data never goes on a blockchain (blockchain anchoring is off).
- [ ] The medical disclaimer "Not a medical diagnosis …" stays on every report and in the footer (already in the app).

## 6. Live demo test checklist

Do these on the deployed site and tick them yourself:

1. [ ] `https://<site>.vercel.app` opens; refreshing on `/knowledge` or `/login` still works (SPA fallback).
2. [ ] `https://breastcare-backend.onrender.com/actuator/health` → `UP`.
3. [ ] `https://<site>.vercel.app/api/knowledge/modules` returns JSON (Vercel → Render forwarding works).
4. [ ] Register a patient account; log in; log out; log in again.
5. [ ] Upload a mammogram from `/prediction`. A result (Benign or Malignant with the probability) appears after up to
       a minute.
6. [ ] Upload a photo that is not a mammogram → a clear "not a mammogram" message.
7. [ ] `/history` lists the scan; the report page opens with the image and heatmap; the PDF downloads.
8. [ ] Try a known benign and a known malignant test image and check the labels match.
9. [ ] A second patient account cannot open the first patient's report URL (it gets an error, not the report).
10. [ ] Admin login works with `ADMIN_EMAIL` / `ADMIN_PASSWORD`.
11. [ ] Turn the laptop off and repeat step 5 from a phone.
12. [ ] Upload a large mammogram (over 5 MB) to check that the Vercel → Render forwarding passes big files.

## 7. Troubleshooting

| Problem | Cause and fix |
|---|---|
| Render log: `Model files missing ... set HF_MODEL_REPO` | `HF_MODEL_REPO` is empty |
| Render log: `curl: (22) ... 401` or `404` | wrong repo name, or the token cannot read the repo |
| Render log: `MONGODB_URI is not set` / timeout connecting | wrong URI, or Atlas Network Access lacks `0.0.0.0/0` |
| Render log: `JWT_SECRET` too short | needs 32+ characters |
| Render restarts during an analysis, `OutOfMemoryError` or "exceeded memory" | the instance is smaller than 4 GB |
| Site loads but login says "forbidden" / 403 | `CORS_ORIGINS` doesn't match the Vercel address exactly (https, no trailing `/`) |
| Every `/api` call returns 404 or an HTML page | the backend address in `Frontend/vercel.json` is wrong |
| Refreshing a page gives 404 | the SPA rule in `vercel.json` was removed |
| First request after a long pause is slow | the service is starting, or downloading the models after a restart |
| Upload of a big file fails at Vercel (413) | use a smaller image, or ask for help to send uploads straight to Render |

## 8. Final URLs

Fill these in after deploying:

- Website: `https://…vercel.app`
- Backend health: `https://breastcare-backend.onrender.com/actuator/health`
- Model repo (private): `https://huggingface.co/<user>/breastcare-models`
