# Kubernetes setup

```
Backend/k8s/
  base/                     shared by every environment (kustomization.yaml; includes Frontend/k8s)
    config/configmap.yaml   non-secret settings (environment variables)
    config/secret.example.yaml   template only, never apply with real values from git
    rbac.yaml               service accounts without API tokens; ci-deployer Role for the pipeline
    storage/pvc.yaml        model weights + encrypted backups
    storage/model-loader.yaml    one-off pod to copy the models in
    backend/                Deployment (1 replica) + ClusterIP Service :8080
    ingress/ingress.yaml    HTTPS: /api and /actuator/health → backend, / → frontend
    network-policy.yaml     default deny + only the needed flows
  overlays/test/            namespace breastcare-test, host test.breastcare.example.com
  overlays/production/      namespace breastcare-production, host breastcare.example.com
Frontend/k8s/               website Deployment (2 replicas) + ClusterIP Service :80
```

There is no separate inference Deployment: the backend runs the ONNX models itself. MongoDB Atlas stays outside the cluster.

## Prerequisites

- A cluster: minikube, kind or k3s for testing, or a cloud cluster. Use `kubectl` 1.27+ (it has Kustomize built in).
- The **ingress-nginx** controller in the namespace `ingress-nginx`. With minikube: `minikube addons enable ingress`.
- **cert-manager** with a ClusterIssuer named `letsencrypt` for automatic TLS, or a TLS secret you create yourself.
- A CNI that enforces NetworkPolicy (Calico, Cilium; minikube: `minikube start --cni=calico`).
- Images in GHCR (CI publishes them) or loaded locally (`minikube image load breastcare-backend:1.0.0`).
- Replace `OWNER` in the image names and `breastcare.example.com` with your values (or let CI set the tags).

## Deploy (test environment)

```bash
NS=breastcare-test
kubectl apply -f Backend/k8s/overlays/test/namespace.yaml

# 1. Secrets: from your terminal or a secret manager, never from git.
kubectl -n $NS create secret generic breastcare-secrets \
  --from-literal=MONGODB_URI='mongodb+srv://USER:PASSWORD@CLUSTER.mongodb.net/breast_cancer' \
  --from-literal=JWT_SECRET="$(openssl rand -base64 48)" \
  --from-literal=BACKUP_ENCRYPTION_KEY="$(openssl rand -base64 32)" \
  --from-literal=ANTHROPIC_API_KEY='sk-ant-...'

# 2. Everything else.
kubectl apply -k Backend/k8s/overlays/test

# 3. Model weights onto the volume (once, and after retraining).
kubectl -n $NS apply -f Backend/k8s/base/storage/model-loader.yaml
kubectl -n $NS wait --for=condition=Ready pod/model-loader
kubectl -n $NS cp ML-Service/weights/. model-loader:/models
kubectl -n $NS delete pod model-loader
kubectl -n $NS rollout restart deployment/breastcare-backend

# 4. Watch it come up.
kubectl -n $NS rollout status deployment/breastcare-backend --timeout=10m
kubectl -n $NS rollout status deployment/breastcare-frontend
kubectl -n $NS get pods,svc,ingress
```

To pin an image tag by hand: `cd Backend/k8s/overlays/test && kustomize edit set image ghcr.io/OWNER/breastcare-backend=ghcr.io/you/breastcare-backend:1.2.0`. CI does this for you.

Without a domain, test with a port-forward: `kubectl -n $NS port-forward svc/breastcare-frontend 8080:80`. Then open http://localhost:8080, which goes through the frontend's own `/api` proxy. With minikube and the ingress addon, put `<minikube ip> test.breastcare.example.com` in your hosts file.

## Ingress and TLS

- `cert-manager.io/cluster-issuer: letsencrypt` makes cert-manager issue `breastcare-tls`. An example ClusterIssuer:
  ```yaml
  apiVersion: cert-manager.io/v1
  kind: ClusterIssuer
  metadata: { name: letsencrypt }
  spec:
    acme:
      server: https://acme-v02.api.letsencrypt.org/directory
      email: you@example.com
      privateKeySecretRef: { name: letsencrypt-account }
      solvers: [{ http01: { ingress: { ingressClassName: nginx } } }]
  ```
- Without cert-manager, create the secret yourself: `kubectl -n $NS create secret tls breastcare-tls --cert=tls.crt --key=tls.key`. Then remove the annotation.
- HTTP is redirected to HTTPS. `AUTH_COOKIE_SECURE=true` (ConfigMap) makes the login cookie HTTPS-only.
- Uploads up to 51 MB and 300 s analysis time are allowed by the ingress annotations.

## Secrets

- Kubernetes Secrets are **base64-encoded, not encrypted**. Anyone who can read Secrets in the namespace can read the values.
- In production, enable encryption at rest (your cloud's KMS, or `EncryptionConfiguration` on self-managed clusters).
- Limit Secret access with RBAC. The `ci-deployer` Role here cannot read Secrets at all.
- Prefer a secret manager: External Secrets Operator with a cloud secret store, Sealed Secrets, or Vault.
- Rotate `JWT_SECRET` to sign everyone out. Keep `BACKUP_ENCRYPTION_KEY` also somewhere outside the cluster: backups cannot be read without it.

## Security in the manifests

- **Pod Security Admission `restricted`** on both namespaces: non-root, seccomp `RuntimeDefault`, all capabilities dropped, no privilege escalation, read-only root filesystem (writable `emptyDir` only where needed).
- **Service accounts:** the app's accounts get **no** API token (`automountServiceAccountToken: false`) and no permissions.
- **CI deployer:** `ci-deployer` may only manage the app's object kinds in its own namespace.
- **NetworkPolicy:** everything is denied by default.
  - frontend ← ingress controller; frontend → backend
  - backend ← ingress controller and frontend
  - backend → internet on 27017 (Atlas) and 443 (Claude API), and an optional in-cluster blockchain node (namespace `blockchain`, port 8545)
  - DNS for all pods

## Health checks

| Container | Probe | Endpoint |
|---|---|---|
| backend | startup (up to 5 min for model loading), liveness | `GET /actuator/health/liveness` |
| backend | readiness (includes MongoDB: no traffic while Atlas is unreachable, but no restart either) | `GET /actuator/health/readiness` |
| frontend | liveness, readiness | `GET /healthz` |

Rolling updates use `maxUnavailable: 0`, so a new pod must be ready before an old one stops.

## Resources and scaling

- **Backend:** requests 0.5 CPU / 1.5 GiB, limit 2 CPU / 3 GiB (the models use about 2 GB).
- **Frontend:** 50m / 64 MiB, limit 0.5 CPU / 128 MiB.
- **Backend stays at 1 replica on purpose.** It runs the daily backup schedule and keeps chat rate limits in memory, and the volumes are ReadWriteOnce. To scale out:
  1. Move backups to a CronJob running the same image with `--backup.run=create`.
  2. Use ReadWriteMany storage.
  3. Accept per-pod chat limits.

The audit log's hash chain is safe across replicas: a unique index plus retry.

## Logs and monitoring

- The backend logs JSON (ECS format: `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`). Collect it with your cluster's log stack (Loki, ELK, cloud logging).
- Alert on these messages:
  - `ML inference failed`
  - `BACKUP failed`
  - `SECURITY repeated failed logins`
  - `BLOCKCHAIN anchoring failed`
  - `failed its integrity check`
- Logs never contain passwords, tokens, emails or medical data. Failed logins log the account id only.
