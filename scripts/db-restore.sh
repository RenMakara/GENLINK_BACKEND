#!/usr/bin/env bash
# GenLink KH — PostgreSQL restore.
# Replaces all database objects after stopping the app; saves a backup first.

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

DUMP_FILE="${1:-}"
if [[ -z "$DUMP_FILE" || ! -f "$DUMP_FILE" ]]; then
  echo "Usage: $0 <path-to.dump>" >&2
  echo "Available backups:" >&2
  ls -1t "${PROJECT_DIR}/backups"/*.dump 2>/dev/null | head -10 >&2 || true
  exit 1
fi

DB_NAME="$(grep -E '^POSTGRES_DB=' .env | cut -d= -f2-)"
DB_USER="$(grep -E '^POSTGRES_USER=' .env | cut -d= -f2-)"

echo "About to RESTORE '${DB_NAME}' from: ${DUMP_FILE}"
echo "This ERASES the current contents of '${DB_NAME}'."
read -rp "Type the database name to confirm: " CONFIRM
if [[ "$CONFIRM" != "$DB_NAME" ]]; then
  echo "Aborted." >&2
  exit 1
fi

# 1. Stop the app so no request writes during the restore. Postgres stays
#    up — we need it to run the restore.
echo "[1/4] Stopping app…"
docker compose stop app || true

# 2. Safety net: dump the current state first. If this restore is a
#    mistake, this file undoes it.
SAFETY="${PROJECT_DIR}/backups/pre-restore_$(date +%Y-%m-%d_%H-%M-%S).dump"
mkdir -p "${PROJECT_DIR}/backups"
echo "[2/4] Safety-dumping current state -> ${SAFETY}"
docker compose exec -T postgres sh -c \
  "PGPASSWORD=\"\$POSTGRES_PASSWORD\" pg_dump -U '${DB_USER}' -d '${DB_NAME}' -Fc" \
  > "$SAFETY"
chmod 600 "$SAFETY"

# 3. Restore.
echo "[3/4] Restoring…"
docker compose exec -T postgres sh -c \
  "PGPASSWORD=\"\$POSTGRES_PASSWORD\" pg_restore -U '${DB_USER}' -d '${DB_NAME}' --clean --if-exists -1" \
  < "$DUMP_FILE"

# 4. Bring the app back. Liquibase re-validates the schema on boot — a
#    green start is your confirmation the restore is coherent.
echo "[4/4] Starting app…"
docker compose up -d app

echo
echo "Restore complete. Safety copy kept at: ${SAFETY}"
echo "Watch it validate the schema:  docker compose logs -f app"
