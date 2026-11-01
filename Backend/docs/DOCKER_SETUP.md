# Docker setup

The app runs as two containers:

| Service | Image | What it does |
|---|---|---|
| `frontend` | `Frontend/Dockerfile`: Node 22 builds the Vue site, unprivileged nginx serves it | Serves the website on port 8080 and proxies `/api` to the backend, so the browser sees one origin and the login cookie and CSRF token work |
| `backend` | `Backend/Dockerfile`: Maven builds the jar, Temurin 21 JRE (Ubuntu) runs it as user 10001 | The Spring Boot API. **It also runs the ML models** (ONNX Runtime), so there is no separate inference container |
| `hardhat` (optional, profile `blockchain`) | `node:22-alpine` | Local test chain for audit proofs (see `BLOCKCHAIN_SETUP.md`) |

- **Database:** MongoDB Atlas stays an external cloud database. No Mongo container, and no credentials in any Docker file.
- **Model weights:** the files in `ML-Service/weights` (not in git) are mounted read-only at `/models`. Without them the site starts and analysis answers 503.

## Requirements

- Docker Engine 24+ or Docker Desktop, with Compose v2.24+ (`docker compose version`).
- `Backend/.env` with your secrets (copy `Backend/.env.example`). At least `MONGODB_URI` and `JWT_SECRET`. Atlas must allow your IP (Network Access).
- Optional Compose settings (image tag, port, model folder) go in the same `Backend/.env` (see the end of `Backend/.env.example`).

## Run

```powershell
cd Backend                            # docker-compose.yml lives here
docker compose up --build -d          # build and start; open http://localhost:8080
docker compose ps                     # both "healthy" after 1-3 minutes (the backend loads the models)
docker compose logs -f backend        # JSON (ECS) logs
docker compose restart backend        # restart one service (for example after editing Backend/.env)
docker compose stop                   # stop, keep containers
docker compose start                  # start them again
docker compose down                   # remove containers (the backups volume is kept)
docker compose --profile blockchain up --build -d   # also start the local Hardhat chain
```

## Build images by hand

```powershell
docker build --build-arg VERSION=1.0.0 -t breastcare-backend:1.0.0  Backend
docker build --build-arg VERSION=1.0.0 -t breastcare-frontend:1.0.0 Frontend
```

Each image is built from its own folder. `Backend/.dockerignore` and `Frontend/.dockerignore` keep `.env` files, backups, `node_modules`, build output and the non-app folders (k8s, docs, blockchain, scripts) out of the build context. The model weights are never in either.

## What the images do and do not contain

- No secrets.
  - The backend reads `MONGODB_URI`, `JWT_SECRET`, `BACKUP_ENCRYPTION_KEY` and `GEMINI_API_KEY` from the environment (`env_file: .env` in `Backend/docker-compose.yml`).
  - Check an image with `docker history --no-trunc <image>` and `docker run --rm --entrypoint env <image>`.
- Non-root users: 10001 for the backend, 101 (nginx) for the frontend.
- Both services run with `no-new-privileges` and all Linux capabilities dropped.
- Health checks:
  - backend `/actuator/health/liveness` in the image, and `/actuator/health/readiness` (includes MongoDB) in Compose
  - frontend `/healthz`
- Version labels: `org.opencontainers.image.version` from `--build-arg VERSION`. CI tags images `sha-<commit>`, `<semver>` and `latest`.
- Backups go to the named volume `backups` (`/data/backups`).

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `env file Backend/.env not found` | Copy `Backend/.env.example` to `Backend/.env` and fill it in |
| Backend never becomes healthy; logs say MongoDB timed out | Atlas Network Access does not allow this machine's IP, or `MONGODB_URI` is wrong |
| Backend restarts with "JWT_SECRET" errors | `JWT_SECRET` missing in `Backend/.env` |
| Analysis returns 503 "Model file not found" | `MODELS_DIR` (default `../ML-Service/weights`, relative to Backend) does not contain the model files |
| Uploads fail with 413 | A proxy in front of the frontend limits body size; nginx here allows 51 MB |
| Login works but the cookie is not kept on `http://` | `AUTH_COOKIE_SECURE` must be `false` for plain HTTP (the default); set `true` behind HTTPS |
| Port 8080 in use | Set `FRONTEND_PORT=8090` in `Backend/.env` |
| The backend is out of memory | Give Docker at least 4 GB; the models need about 2 GB |
