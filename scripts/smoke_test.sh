#!/usr/bin/env bash
# End-to-end check: tries the main features and the security rules with curl.
# Used by GitHub Actions, and you can run it yourself.
#
#   bash scripts/smoke_test.sh
#       starts the core API (H2) + analysis service (stub mode) itself.
#       Needs core-api/target/core-api.jar built and the Python packages installed.
#
#   MODE=docker bash scripts/smoke_test.sh
#       tests an already running, FRESH Docker stack started with STUB_MODE=true:
#         docker compose down -v && STUB_MODE=true docker compose up -d --build --wait
set -u
cd "$(dirname "$0")/.."

PYTHON=${PYTHON:-python3}
FAILED=0
pass() { echo "PASS  $1"; }
fail() { echo "FAIL  $1"; FAILED=1; }
check() { # check "name" expected actual
  if [ "$2" = "$3" ]; then pass "$1"; else fail "$1 (expected $2, got $3)"; fi
}
code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }

MODE=${MODE:-local}
WORK=$(mktemp -d)

if [ "$MODE" = "local" ]; then
  # throwaway database + passwords + key for this test only
  export DB_FILE_PASSWORD=smoke-file-pass DB_PASSWORD=smoke-user-pass STUB_MODE=true CORE_API_URL=http://localhost:8080
  export DATA_ENCRYPTION_KEY=$(head -c 32 /dev/urandom | base64)
  # stop if something (e.g. Docker, or an old run) is already using the ports
  for port in 8080 8000; do
    if curl -s -o /dev/null "localhost:$port"; then
      echo "Port $port is busy. Stop the other copy first (docker compose down?)."; exit 1
    fi
  done
  # "exec" so the saved PID is the server itself, and kill really stops it
  (cd "$WORK" && exec java -jar "$OLDPWD/core-api/target/core-api.jar" > "$WORK/core.log" 2>&1) &
  CORE_PID=$!
  (cd analysis-service && exec $PYTHON -m uvicorn main:app --port 8000 > "$WORK/analysis.log" 2>&1) &
  ANALYSIS_PID=$!
fi
cleanup() {
  [ "$MODE" = "local" ] && kill $CORE_PID $ANALYSIS_PID 2>/dev/null
  wait 2>/dev/null
  rm -rf "$WORK"
}
trap cleanup EXIT

for i in $(seq 1 60); do
  curl -s localhost:8080/api/health > /dev/null && curl -s localhost:8000/health > /dev/null && break
  sleep 1
done

check "core API is up"            200 "$(code localhost:8080/api/health)"
check "analysis service is up"    200 "$(code localhost:8000/health)"
curl -s -XPOST localhost:8000/reload-config > /dev/null

CLIP="$WORK/clip.webm"; head -c 2000 /dev/urandom > "$CLIP"
check "no analysis before consent" 403 "$(code -F session_id=t -F "audio=@$CLIP;type=audio/webm" localhost:8000/analyze)"

check "give consent" 200 "$(code -XPUT -H 'Content-Type: application/json' -d '{"given":true}' localhost:8080/api/config/consent)"
check "save settings" 200 "$(code -XPUT -H 'Content-Type: application/json' localhost:8080/api/config \
  -d '{"codeWords":"red umbrella","cancelCodeWord":"false alarm","sensitivity":"MEDIUM","disguiseEnabled":false,"disguiseType":"calculator","duressPin":"4821","analysisActive":true,"shareLocation":true}')"
curl -s -XPOST localhost:8000/reload-config > /dev/null

check "analyze a clip"            200 "$(code -F session_id=t -F "audio=@$CLIP;type=audio/webm" localhost:8000/analyze)"
check "forced alert reaches core" 200 "$(code -F session_id=t2 -F force_alert=true -F "audio=@$CLIP;type=audio/webm" localhost:8000/analyze)"
sleep 1
ALERTS=$(curl -s localhost:8080/api/alerts)
case "$ALERTS" in *nonverbal*) pass "alert was saved";; *) fail "alert was saved";; esac
case "$ALERTS" in *ackToken*) fail "ack token leaked in alert list";; *) pass "ack token not in alert list";; esac

CONFIG=$(curl -s localhost:8080/api/config)
case "$CONFIG" in *4821*) fail "duress PIN leaked";; *) pass "duress PIN not in config";; esac
check "PIN check" '{"match":true}' "$(curl -s -XPOST -H 'Content-Type: application/json' -d '{"pin":"4821"}' localhost:8080/api/disguise/check-pin)"

check "other website blocked (analysis)" 403 "$(code -XPOST -H 'Origin: https://evil.example' localhost:8000/calibrate/demo)"
check "other website blocked (core)"     403 "$(code -XPOST -H 'Origin: https://evil.example' localhost:8080/api/alerts/1/cancel)"
check "foreign Host blocked (core)"      403 "$(code -H 'Host: attacker.example' localhost:8080/api/config)"
check "foreign Host blocked (analysis)"  400 "$(code -H 'Host: attacker.example' localhost:8000/health)"
check "guessed ack link rejected"        404 "$(code localhost:8080/api/alerts/ack/1)"

# Is the saved data unreadable for someone who opens the database?
sleep 1
if [ "$MODE" = "docker" ]; then
  RAW=$(docker compose exec -T db psql -U silentsignal -d silentsignal -tAc "select code_words from app_config")
  case "$RAW" in
    "") fail "could not read the database";;
    *"red umbrella"*) fail "code words stored as plain text";;
    *) pass "sensitive columns are encrypted in PostgreSQL";;
  esac
elif [ ! -f "$WORK/data/distressdb.mv.db" ]; then
  fail "database file was not created"
elif grep -q "red umbrella" "$WORK/data/distressdb.mv.db"; then
  fail "database file is readable (not encrypted)"
else
  pass "database file is encrypted"
fi

check "delete everything" 204 "$(code -XDELETE localhost:8080/api/data)"
check "contacts gone" "[]" "$(curl -s localhost:8080/api/contacts)"

if [ $FAILED -ne 0 ]; then
  if [ "$MODE" = "docker" ]; then
    docker compose logs --tail 30
  else
    echo "--- core log";     tail -30 "$WORK/core.log"
    echo "--- analysis log"; tail -30 "$WORK/analysis.log"
  fi
  exit 1
fi
echo "All smoke tests passed."
