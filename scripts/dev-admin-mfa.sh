#!/usr/bin/env bash
#
# Drives the admin MFA flows end to end, because the challenge token lives for five minutes -
# not long enough to copy three values between a terminal and an authenticator app.
#
#   ./scripts/dev-admin-mfa.sh you@example.com 'Password'            enrol, code generated here
#   ./scripts/dev-admin-mfa.sh you@example.com 'Password' --manual   enrol, code from your app
#   ./scripts/dev-admin-mfa.sh you@example.com 'Password' --login    sign in (ADM-023)
#
# DEVELOPMENT ONLY. The password is a command-line argument, so it lands in your shell history.
#
# The default enrol mode generates the code itself, which proves the SERVER works and says
# nothing about whether a real authenticator agrees with the otpauth:// URI. --manual is the
# mode that tests that, and it is the only way to verify ADM-022 AC#1.
#
# Env: BASE_URL (default http://localhost:8888)
#
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8888}"
API="${BASE_URL}/api/v1/admin/auth"

EMAIL="${1:-}"
PASSWORD="${2:-}"
MODE="${3:-}"

if [ -z "$EMAIL" ] || [ -z "$PASSWORD" ]; then
    echo "usage: $0 <email> <password> [--manual|--login]" >&2
    exit 64
fi

PY="$(command -v python3 || command -v python)" || {
    echo "python is required (JSON parsing and TOTP)" >&2
    exit 69
}

step() { printf '\n\033[1m%s\033[0m\n' "$*"; }

# Reads one field out of the ApiResponse envelope. python rather than jq: jq is not installed
# by default on Git Bash, and everyone here already has python.
field() {
    "$PY" -c "
import sys, json
try:
    body = json.load(sys.stdin)
except ValueError:
    sys.exit('not JSON - is the app running on ${BASE_URL}?')
data = body.get('data') or {}
value = data.get('$1')
if value is None:
    sys.exit('%s: %s' % (body.get('errorCode', 'ERROR'), body.get('message', 'no message')))
print(value)
"
}

# RFC 6238, same parameters as the server: 30-second step, 6 digits, HMAC-SHA1.
totp() {
    "$PY" -c "
import base64, hmac, hashlib, struct, time, sys
raw = sys.argv[1]
key = base64.b32decode(raw + '=' * ((8 - len(raw) % 8) % 8))
digest = hmac.new(key, struct.pack('>Q', int(time.time() // 30)), hashlib.sha1).digest()
offset = digest[-1] & 0x0F
binary = ((digest[offset] & 0x7F) << 24 | (digest[offset + 1] & 0xFF) << 16
          | (digest[offset + 2] & 0xFF) << 8 | (digest[offset + 3] & 0xFF))
print(str(binary % 10 ** 6).zfill(6))
" "$1"
}

# curl -s is silent on a refused connection, and `set -e` would then abort with a bare exit
# code and no explanation.
post() {
    local url="$1"
    shift
    local body code

    # `set +e` rather than `if ! body=$(...)`: in that form $? is the result of the negation,
    # not curl's exit status, so the reported code would always be 1.
    set +e
    body=$(curl -s --fail-with-body "$@" "$url" 2>/dev/null)
    code=$?
    set -e

    # A non-zero exit with a body is normal - /login answers 401 on success. Only an empty
    # body means the server was never reached (7 = refused, 28 = timed out).
    if [ -n "$body" ]; then
        printf '%s' "$body"
        return 0
    fi
    echo "cannot reach ${BASE_URL} (curl exit ${code}) - is the app running?" >&2
    exit 69
}

# ---------------------------------------------------------------------------
step "1. Password step"

LOGIN=$(post "${API}/login" -X POST \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}")

CHALLENGE=$(printf '%s' "$LOGIN" | field challengeToken)
ENROLMENT_REQUIRED=$(printf '%s' "$LOGIN" | field enrolmentRequired)

echo "   challenge issued, valid 5 minutes"
echo "   enrolmentRequired: ${ENROLMENT_REQUIRED}"

is_enrolled() { [ "$ENROLMENT_REQUIRED" = "False" ] || [ "$ENROLMENT_REQUIRED" = "false" ]; }

# ---------------------------------------------------------------------------
if [ "$MODE" = "--login" ]; then
    step "2. Sign in (ADM-023)"

    if ! is_enrolled; then
        echo "   This admin has not completed TOTP enrolment - run without --login first." >&2
        exit 1
    fi

    # Prompted, never generated. The secret is encrypted server-side and is returned exactly
    # once at enrolment, so there is nothing here to compute a code from - which is the point.
    printf '   Enter the 6-digit code from your authenticator (or a recovery code): '
    read -r CODE

    VERIFY=$(post "${API}/mfa/verify" -X POST \
        -H "Authorization: Bearer ${CHALLENGE}" \
        -H 'Content-Type: application/json' \
        -d "{\"code\":\"${CODE}\"}")

    printf '%s' "$VERIFY" | "$PY" -c "
import sys, json
body = json.load(sys.stdin)
data = body.get('data') or {}
if not data.get('accessToken'):
    sys.exit('   %s: %s' % (body.get('errorCode', 'ERROR'), body.get('message', 'no message')))
print('   signed in.\n')
print('   access token  : %s...' % data['accessToken'][:40])
print('   expires in    : %ss' % data['expiresInSeconds'])
print('   refresh token : %s...' % data['refreshToken'][:40])
remaining = data.get('recoveryCodesRemaining')
if remaining is not None:
    print('\n   You used a RECOVERY CODE. %s left.' % remaining)
    if remaining <= 3:
        print('   Regenerate soon - after the last one there is no self-service way back.')
"

    cat <<'LOGGEDIN'

   The access token is typ=ADMIN: accepted only on /api/v1/admin/**, and refused
   on mobile routes with ADMIN_TOKEN_TYPE_INVALID.

   The challenge is now spent. A second /mfa/verify with the same one is refused
   even with a correct code - sign in again for a fresh challenge.

   There is not much to call with it yet: /admin/auth/me, refresh and logout are
   ADM-024.
LOGGEDIN
    exit 0
fi

# ---------------------------------------------------------------------------
if is_enrolled; then
    cat >&2 <<'ALREADY'

   This admin is already enrolled, so /mfa/confirm will refuse with
   ADMIN_MFA_ALREADY_ENROLLED - recovery codes are issued exactly once.

   To sign in instead, re-run with --login.

   To start over (development only):
     UPDATE admin_users SET totp_secret=NULL, totp_enabled=false,
            totp_confirmed_at=NULL, recovery_codes=NULL;
ALREADY
    exit 1
fi

step "2. Enrol"

ENROL=$(post "${API}/mfa/enrol" -X POST -H "Authorization: Bearer ${CHALLENGE}")
SECRET=$(printf '%s' "$ENROL" | field secret)
URI=$(printf '%s' "$ENROL" | field otpauthUri)

echo "   secret: ${SECRET}"
echo "   uri:    ${URI}"

if [ "$MODE" = "--manual" ]; then
    cat <<MANUAL

   >>> DELETE any previous GenLink entry in your authenticator first. <<<

   Every run of this script calls /mfa/enrol again, and each call generates a
   NEW secret and discards the one before it. That is deliberate - an admin who
   loses the QR page has to be able to start over, and an unconfirmed secret
   grants nothing - but it means a code from a previous run will ALWAYS be
   rejected as BAD_CODE. If you are seeing repeated failures, this is why.

   Add THIS secret to your authenticator now:
     - scan the URI above as a QR code (offline: qrencode -t ANSI "\$URI"), or
     - "Enter a setup key" and paste ${SECRET}, type = time-based

   Do not paste the URI into an online QR generator. It contains the secret.

MANUAL
    printf '   Enter the 6-digit code from your app: '
    read -r CODE
else
    CODE=$(totp "$SECRET")
    echo "   generated code: ${CODE}"
fi

# ---------------------------------------------------------------------------
step "3. Confirm"

CONFIRM=$(post "${API}/mfa/confirm" -X POST \
    -H "Authorization: Bearer ${CHALLENGE}" \
    -H 'Content-Type: application/json' \
    -d "{\"code\":\"${CODE}\"}")

printf '%s' "$CONFIRM" | "$PY" -c "
import sys, json
body = json.load(sys.stdin)
data = body.get('data') or {}
codes = data.get('recoveryCodes')
if not codes:
    sys.exit('   %s: %s' % (body.get('errorCode', 'ERROR'), body.get('message', 'no message')))
print('   TOTP is now active.\n')
print('   Recovery codes - shown once, stored hashed, not retrievable again:\n')
for i in range(0, len(codes), 2):
    print('     ' + '   '.join(codes[i:i + 2]))
"

cat <<'DONE'

   Verify what was persisted:
     SELECT left(totp_secret, 3) AS marker, totp_enabled, totp_confirmed_at
     FROM admin_users;

   marker should read "v1:" and the rest of the column should be unreadable.

   NOTE: under a dev/local profile with no ADMIN_CRYPTO_KEY set, FieldEncryptor
   generates an ephemeral key - this enrolment will NOT survive a restart. Set
   ADMIN_CRYPTO_KEY (openssl rand -base64 32) to keep it.

   Now sign in:  ./scripts/dev-admin-mfa.sh <email> <password> --login
DONE
