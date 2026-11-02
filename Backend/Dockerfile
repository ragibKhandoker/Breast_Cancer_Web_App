# syntax=docker/dockerfile:1.7
# BreastCare backend (Spring Boot 4, Java 21). The ML models run inside this service (ONNX Runtime), so there is
# no separate inference container: the weights are mounted read-only at /models at run time, never baked in.
# Build from this folder:  cd Backend; docker build -t breastcare-backend:1.0.0 .

# ---- Build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Dependencies first, so code changes reuse the cached layer.
COPY pom.xml ./pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -Dmaven.test.skip=true package \
    && cp target/backend-*.jar /app.jar

# ---- Run ----
# Ubuntu-based JRE: ONNX Runtime's native library needs glibc (Alpine's musl does not work).
FROM eclipse-temurin:21-jre-noble
ARG VERSION=dev
LABEL org.opencontainers.image.title="breastcare-backend" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.source="https://github.com/OWNER/Cancer_web"
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl libgomp1 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app /models /data/backups \
    && chown -R app:app /app /data /models
WORKDIR /app
COPY --from=build --chown=app:app /app.jar /app/app.jar
# Checks the model files in /models, or downloads them from a private Hugging Face repo (HF_MODEL_REPO, HF_TOKEN).
COPY --chmod=0755 scripts/docker-entrypoint.sh /app/docker-entrypoint.sh

# Non-secret defaults only. MONGODB_URI, JWT_SECRET, BACKUP_ENCRYPTION_KEY and other secrets come from the
# environment (docker compose env_file / Kubernetes Secret), never from the image.
ENV SERVER_PORT=8080 \
    MODEL_PATH=/models/model.onnx \
    HEATMAP_PATH=/models/heatmap.onnx \
    GATE_PATH=/models/gate/gate.onnx \
    MAMMOSCREEN_DIR=/models/mammoscreen \
    ENSEMBLE_PATH=/models/ensemble/ensemble.json \
    FIRSTPLACE_PATH=/models/firstplace/firstplace_fold0.onnx \
    FINAL_PATH=/models/final/final.json \
    BACKUP_DIR=/data/backups \
    LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

USER 10001:10001
EXPOSE 8080
# Liveness: the app is running (MongoDB is checked by readiness, so a database outage does not restart it).
HEALTHCHECK --interval=30s --timeout=5s --start-period=300s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["/app/docker-entrypoint.sh"]
