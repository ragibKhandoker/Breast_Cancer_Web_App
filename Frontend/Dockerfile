# syntax=docker/dockerfile:1.7
# BreastCare website (Vue 3 + Vite), served by unprivileged nginx. nginx also proxies /api to the backend, so the
# browser talks to one origin and the login cookie and CSRF token work as in development.
# Build from this folder:  cd Frontend; docker build -t breastcare-frontend:1.0.0 .

# ---- Build ----
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . ./
# vite.config.js writes into the backend for the single-jar setup; here the site goes to dist/ instead.
RUN npx vite build --outDir dist --emptyOutDir

# ---- Run ----
FROM nginxinc/nginx-unprivileged:1.27-alpine
ARG VERSION=dev
LABEL org.opencontainers.image.title="breastcare-frontend" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.source="https://github.com/OWNER/Cancer_web"
# The template is filled from the environment (BACKEND_URL) when the container starts.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist /usr/share/nginx/html
ENV BACKEND_URL=http://backend:8080
USER 101
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
