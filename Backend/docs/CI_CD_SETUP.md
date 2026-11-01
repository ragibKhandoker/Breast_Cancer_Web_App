# CI/CD with GitHub Actions

## Workflows

**`.github/workflows/ci.yml`** runs on every push and pull request.

| Job | What |
|---|---|
| `frontend-test` | `npm ci`, `npm test` (Vitest), production build |
| `backend-test` | `./mvnw test`, against a throwaway local MongoDB (never Atlas). Tests that need the trained models (not in git) skip themselves |
| `blockchain-test` | Hardhat contract tests |
| `dependency-scan` | Trivy filesystem scan of npm and Maven dependencies and committed secrets. Prints HIGH and CRITICAL findings; fails on fixable CRITICAL ones |
| `images` (after all of the above) | Builds the backend and frontend images and scans each with Trivy (fails on fixable CRITICAL). Pushes to GHCR only from `main` and `v*` tags, never from pull requests |

Images are tagged `sha-<7-char commit>`, `<semver>` from tags like `v1.2.0`, and `latest` on `main`:
- `ghcr.io/<owner>/breastcare-backend`
- `ghcr.io/<owner>/breastcare-frontend`

**`.github/workflows/deploy.yml`** deploys after a successful CI run on `main`, or by hand (*Run workflow* with a tag).

1. `deploy-test`: environment `test`. Sets the image tag in `Backend/k8s/overlays/test`, applies it, and waits for both rollouts.
2. `deploy-production`: environment `production`. **Waits for a reviewer's approval**, then does the same with `Backend/k8s/overlays/production`.

The shared steps are in `.github/actions/deploy/action.yml`.

## One-time setup

1. **GHCR.** Nothing to create. `GITHUB_TOKEN` with `packages: write` (granted in the `images` job) publishes the images. After the first push, open each package's settings: link it to the repository, and make it private or public. A private package needs an image pull secret in the cluster:
   ```bash
   kubectl -n breastcare-test create secret docker-registry ghcr --docker-server=ghcr.io \
     --docker-username=<user> --docker-password=<PAT with read:packages>
   kubectl -n breastcare-test patch serviceaccount breastcare-backend -p '{"imagePullSecrets":[{"name":"ghcr"}]}'
   kubectl -n breastcare-test patch serviceaccount breastcare-frontend -p '{"imagePullSecrets":[{"name":"ghcr"}]}'
   ```
2. **Environments** (Settings → Environments):
   - `test`: secret `KUBE_CONFIG` = a kubeconfig for the test cluster.
   - `production`: secret `KUBE_CONFIG` for production. Under **Required reviewers**, add the people who approve releases, and optionally restrict deployment branches to `main`.
3. **Kubeconfig with least privilege.** Use the `ci-deployer` service account from `Backend/k8s/base/rbac.yaml` (namespace-only, no Secrets), not an admin config:
   ```bash
   kubectl -n breastcare-test create token ci-deployer --duration=720h   # put this token in a kubeconfig user entry
   ```
   Renew it before it expires. A cloud provider's OIDC federation (no stored token) is better where available.
4. **App secrets** (`breastcare-secrets`) are created in each namespace by an operator (see `KUBERNETES_SETUP.md`). The pipeline never handles them.
5. Replace `breastcare.example.com` in the overlays and the environment URLs with your hosts.

## Secret hygiene

- Workflow secrets are passed through environment variables, never interpolated into commands.
- The kubeconfig is written with mode 600 and deleted after the job.
- GitHub masks secret values in logs. Do not `echo` them, and do not enable `set -x` in steps that touch them.
- Pull requests from forks get no secrets and never push images or deploy.

## A release

```bash
git tag v1.2.0 && git push origin v1.2.0   # CI builds and pushes :1.2.0
```

Then *Actions → Deploy → Run workflow* with tag `1.2.0`. It deploys to test; approve the production step when test looks right.

## Rolling back

Re-run *Deploy* with the previous tag, or `kubectl -n breastcare-production rollout undo deployment/breastcare-backend`.
