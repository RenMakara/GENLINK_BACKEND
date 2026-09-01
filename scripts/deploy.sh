#!/usr/bin/env bash
#
# GenLink KH — deploy the latest main to this EC2 instance.
#
# Runs ON the EC2 instance (invoked by the GitHub Actions SSH step, or by hand):
#   ./scripts/deploy.sh
#
# Safe by construction:
#   * a compile failure never touches the running app (build happens before
#     the container is swapped, and set -e aborts on failure);
#   * a runtime failure auto-rolls-back to the previous image;
#   * only the `app` service is rebuilt — Postgres is never restarted.

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

APP_IMAGE="genlink-api:latest"
ROLLBACK_IMAGE="genlink-api:rollback"
CONTAINER="genlink-api"
HEALTH_TIMEOUT=120   # seconds to wait for the new container to go healthy

log() { echo "[$(date -Is)] $*"; }

# 1. Pull the new code. --ff-only refuses to create a merge commit: if the
#    server checkout has diverged (someone edited a tracked file here), the
#    deploy stops loudly instead of silently merging. .env is gitignored, so
#    it is never touched.
log "Pulling latest main…"
git fetch --quiet origin main
BEFORE="$(git rev-parse --short HEAD)"
git pull --ff-only origin main
AFTER="$(git rev-parse --short HEAD)"

if [[ "$BEFORE" == "$AFTER" ]]; then
  log "Already at ${AFTER} — nothing to deploy."
  exit 0
fi
log "Deploying ${BEFORE} -> ${AFTER}"

# 2. Save the currently-running image so we can roll back to it. `|| true`
#    for the very first deploy, when no previous image exists yet.
if docker image inspect "$APP_IMAGE" >/dev/null 2>&1; then
  docker image tag "$APP_IMAGE" "$ROLLBACK_IMAGE"
  log "Tagged current image as rollback."
fi

# 3. Build the new image. If compilation fails, set -e aborts HERE — the old
#    container is still running and untouched, so a broken commit cannot
#    take production down.
log "Building app image…"
docker compose build app

# 4. Swap in the new container. Postgres is left alone.
log "Starting new container…"
docker compose up -d app

# 5. Wait for the container's own healthcheck to report healthy.
log "Waiting for health (up to ${HEALTH_TIMEOUT}s)…"
deadline=$(( $(date +%s) + HEALTH_TIMEOUT ))
status="starting"
while (( $(date +%s) < deadline )); do
  status="$(docker inspect --format '{{.State.Health.Status}}' "$CONTAINER" 2>/dev/null || echo none)"
  [[ "$status" == "healthy" ]] && break
  [[ "$status" == "unhealthy" ]] && break
  sleep 5
done

# 6. On success, tidy up. On failure, roll back to the previous image.
if [[ "$status" == "healthy" ]]; then
  log "Healthy. Deploy of ${AFTER} succeeded."
  # Remove dangling images left by the build so the disk does not fill.
  docker image prune -f >/dev/null 2>&1 || true
  exit 0
fi

log "ERROR: new container is '${status}', not healthy. Rolling back…"
docker compose logs --tail 40 app || true

if docker image inspect "$ROLLBACK_IMAGE" >/dev/null 2>&1; then
  docker image tag "$ROLLBACK_IMAGE" "$APP_IMAGE"
  docker compose up -d app
  log "Rolled back to the previous image. Investigate the logs above."
else
  log "No rollback image available (first deploy). App is down — fix and redeploy."
fi
exit 1
