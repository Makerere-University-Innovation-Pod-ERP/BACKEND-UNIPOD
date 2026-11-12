#!/usr/bin/env bash
#
# Smoke test for David's endpoints (D1 authorize, D3 audit log).
# Runs against any running backend: local or the hosted one on Render.
#
# Usage:
#   BASE_URL=https://backend-unipod.onrender.com/api \
#   ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD='...' \
#   ./scripts/smoke-test.sh [--write]
#
# Without --write it only reads. With --write it also creates one throwaway
# SUBMITTER account (smoke-<time>@example.com). That makes an audit line we can
# look for, and gives us a second user to check the 403 refusal with.
# Accounts can't be deleted yet, so only use --write on a test environment.
#
# Optional instead of --write: SUBMITTER_EMAIL and SUBMITTER_PASSWORD of an
# existing submitter account, to check the 403 without creating anything.
#
# Needs: bash, curl, python3. Exit code is the number of failed checks.

set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
BASE_URL="${BASE_URL%/}"
WRITE=false
[[ "${1:-}" == "--write" ]] && WRITE=true

: "${ADMIN_EMAIL:?Set ADMIN_EMAIL}"
: "${ADMIN_PASSWORD:?Set ADMIN_PASSWORD}"

PASSED=0
FAILED=0
BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT

green() { printf '\033[32m%s\033[0m\n' "$1"; }
red()   { printf '\033[31m%s\033[0m\n' "$1"; }
pass()  { PASSED=$((PASSED + 1)); green "  PASS  $1"; }
fail()  { FAILED=$((FAILED + 1)); red   "  FAIL  $1"; [[ -s "$BODY_FILE" ]] && echo "        body: $(head -c 400 "$BODY_FILE")"; }

# call METHOD PATH [TOKEN] [JSON_BODY] -> sets STATUS, body in $BODY_FILE
call() {
    local method="$1" path="$2" token="${3:-}" data="${4:-}"
    local args=(-s -o "$BODY_FILE" -w '%{http_code}' --max-time 90 -X "$method"
                -H 'Accept: application/json' -H "X-Request-Id: smoke-$(date +%s)")
    [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
    [[ -n "$data" ]] && args+=(-H 'Content-Type: application/json' --data "$data")
    STATUS="$(curl "${args[@]}" "$BASE_URL$path")"
}

# json EXPRESSION -> evaluates a python expression against the last body as `j`
json() {
    python3 -c "import json,sys; j=json.load(open(sys.argv[1])); print($1)" "$BODY_FILE" 2>/dev/null
}

expect_status() {
    local want="$1" label="$2"
    if [[ "$STATUS" == "$want" ]]; then pass "$label ($STATUS)"; else fail "$label: wanted $want, got $STATUS"; fi
}

expect_json() {
    local expr="$1" want="$2" label="$3" got
    got="$(json "$expr")"
    if [[ "$got" == "$want" ]]; then pass "$label"; else fail "$label: wanted '$want', got '$got'"; fi
}

login() {
    local email="$1" password="$2" payload
    payload="$(python3 -c 'import json,sys; print(json.dumps({"email": sys.argv[1], "password": sys.argv[2]}))' "$email" "$password")"
    call POST /auth/login "" "$payload"
    [[ "$STATUS" == "200" ]] && json "j['accessToken']"
}

echo "Smoke testing $BASE_URL"
echo

echo "== Health (Render may need up to a minute to wake up)"
call GET /actuator/health
expect_status 200 "backend is up"

echo
echo "== D1: sign in"
ADMIN_TOKEN="$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
if [[ -n "$ADMIN_TOKEN" ]]; then pass "admin can sign in"; else fail "admin sign in (status $STATUS)"; red "Cannot continue without an admin token."; exit 1; fi

echo
echo "== D3: audit log, GET /activity"
call GET /activity
expect_status 401 "no token is refused"

call GET "/activity?size=5" "$ADMIN_TOKEN"
expect_status 200 "admin can read the audit log"
expect_json "sorted(j.keys()) == ['items','page','size','totalElements','totalPages']" "True" "response is a page (items, page, size, totalElements, totalPages)"

call GET "/activity?from=2026-11-12&to=2026-11-01" "$ADMIN_TOKEN"
expect_status 400 "'from' after 'to' is rejected"

call GET "/activity?size=500" "$ADMIN_TOKEN"
expect_status 400 "page size over 100 is rejected"

echo
echo "== D3: brief history, GET /cases/{id}/activity"
call GET "/cases/00000000-0000-0000-0000-000000000000/activity" "$ADMIN_TOKEN"
expect_status 404 "unknown brief is 404"

SUBMITTER_TOKEN=""
if $WRITE; then
    echo
    echo "== D3: creating a user writes an audit line (--write)"
    SMOKE_EMAIL="smoke-$(date +%s)@example.com"
    SMOKE_PASSWORD="smoke-$(date +%s)-$RANDOM-password"
    call POST /admin/users "$ADMIN_TOKEN" \
        "$(python3 -c 'import json,sys; print(json.dumps({"email": sys.argv[1], "password": sys.argv[2], "role": "SUBMITTER"}))' "$SMOKE_EMAIL" "$SMOKE_PASSWORD")"
    expect_status 201 "admin creates $SMOKE_EMAIL"
    NEW_ID="$(json "j['id']")"

    call GET "/activity?action=user.created&entityId=$NEW_ID" "$ADMIN_TOKEN"
    expect_status 200 "search the audit log for that user"
    expect_json "j['totalElements']" "1" "exactly one user.created line"
    expect_json "j['items'][0]['after']['email']" "$SMOKE_EMAIL" "line shows the new user's email"
    expect_json "j['items'][0]['actor']" "$(echo "$ADMIN_EMAIL" | tr '[:upper:]' '[:lower:]')" "line shows who did it"
    expect_json "'passwordHash' in json.dumps(j)" "False" "no password hash in the log"

    SUBMITTER_TOKEN="$(login "$SMOKE_EMAIL" "$SMOKE_PASSWORD")"
elif [[ -n "${SUBMITTER_EMAIL:-}" && -n "${SUBMITTER_PASSWORD:-}" ]]; then
    SUBMITTER_TOKEN="$(login "$SUBMITTER_EMAIL" "$SUBMITTER_PASSWORD")"
fi

echo
echo "== D1: authorize() refuses with a code and reason"
if [[ -n "$SUBMITTER_TOKEN" ]]; then
    call GET /activity "$SUBMITTER_TOKEN"
    expect_status 403 "submitter cannot read the audit log"
    expect_json "j.get('code')" "ROLE_NOT_PERMITTED" "refusal carries code ROLE_NOT_PERMITTED"
    expect_json "j.get('detail')" "Your role is not allowed to do this." "refusal carries a readable reason"
else
    echo "  SKIP  run with --write, or set SUBMITTER_EMAIL and SUBMITTER_PASSWORD"
fi

echo
if [[ "$FAILED" -eq 0 ]]; then green "All $PASSED checks passed."; else red "$FAILED failed, $PASSED passed."; fi
exit "$FAILED"
