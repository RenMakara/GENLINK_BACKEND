#!/usr/bin/env bash
# GenLink KH — PostgreSQL backup.

set -Eeuo pipefail
# -E ERR trap survives into functions -e abort on any failing command.

# Resolve the project directory from this script's location, so cron can
# call it by absolute path without caring about the working directory.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

BACKUP_DIR="${BACKUP_DIR:-$PROJECT_DIR/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
SERVICE="postgres"

# Read POSTGRES_* from .env without exporting the whole file into the environment (it also holds JWT.
if [[ ! -f .env ]]; then
  echo "ERROR: .env not found in $PROJECT_DIR" >&2
  exit 1
fi
DB_NAME="$(grep -E '^POSTGRES_DB=' .env | cut -d= -f2-)"
DB_USER="$(grep -E '^POSTGRES_USER=' .env | cut -d= -f2-)"

if [[ -z "${DB_NAME}" || -z "${DB_USER}" ]]; then
  echo "ERROR: POSTGRES_DB / POSTGRES_USER missing from .env" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
# Dumps contain every user record and password hash. Owner-only.
chmod 700 "$BACKUP_DIR"

TIMESTAMP="$(date +%Y-%m-%d_%H-%M-%S)"
OUTFILE="$BACKUP_DIR/genlink_${TIMESTAMP}.dump"

echo "[$(date -Is)] Backing up '${DB_NAME}' -> ${OUTFILE}"

# -T disables Docker's TTY allocation.
docker compose exec -T "$SERVICE" sh -c \
  "PGPASSWORD=\"\$POSTGRES_PASSWORD\" pg_dump -U '${DB_USER}' -d '${DB_NAME}' -Fc" \
  > "$OUTFILE"

chmod 600 "$OUTFILE"

# A dump of a failed run can still be a small non-empty file, so check the
# size rather than merely that the file exists.
SIZE_BYTES="$(stat -c%s "$OUTFILE")"
if (( SIZE_BYTES < 1024 )); then
  echo "ERROR: dump is only ${SIZE_BYTES} bytes — treating as failed" >&2
  rm -f "$OUTFILE"
  exit 1
fi

# Verify the archive is readable by pg_restore.
if ! docker compose exec -T "$SERVICE" sh -c \
     'cat > /tmp/verify.dump && pg_restore --list /tmp/verify.dump > /dev/null 2>&1; rc=$?; rm -f /tmp/verify.dump; exit $rc' \
     < "$OUTFILE"; then
  echo "ERROR: dump failed verification — not a valid pg_restore archive" >&2
  rm -f "$OUTFILE"
  exit 1
fi

echo "[$(date -Is)] OK — $(du -h "$OUTFILE" | cut -f1) verified"

# Rotate only AFTER a verified success, so a run of failures can never
# delete the last good backup.
DELETED="$(find "$BACKUP_DIR" -name 'genlink_*.dump' -mtime "+${RETENTION_DAYS}" -print -delete | wc -l)"
echo "[$(date -Is)] Retention ${RETENTION_DAYS}d — removed ${DELETED} old backup(s)"
