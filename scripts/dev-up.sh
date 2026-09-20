#!/usr/bin/env bash
# One command for local dev: bootRun + the permanent ngrok tunnel.
#
#   ./scripts/dev-up.sh            # app on :8089 + tunnel
#   ./scripts/dev-up.sh --debug    # same, plus a JDWP debugger on :5005
#                                  # (IntelliJ: Debug → Remote JVM: host localhost, port 5005)
#
# The ngrok domain is pinned in the global ngrok config (tunnel "cleancars" →
# omit-strainer-player.ngrok-free.dev), so Razorpay's Dashboard webhook URL is
# set once and survives restarts:
#
#   https://omit-strainer-player.ngrok-free.dev/api/webhooks/razorpay
#
# Optional: scripts/.dev.env (gitignored) holds startup env vars, e.g.
#   RAZORPAY_KEY_ID=...
#   RAZORPAY_KEY_SECRET=...
#   RAZORPAY_WEBHOOK_SECRET=...
# so a plain `./scripts/dev-up.sh` brings up the app with billing wired.

set -euo pipefail
cd "$(dirname "$0")/.."

ENV_FILE="scripts/.dev.env"
if [ -f "$ENV_FILE" ]; then
  set -a; . "$ENV_FILE"; set +a
fi

# MySQL first — bootRun needs it; a fresh PC boot starts with nothing running.
DEBUG=false
[ "${1:-}" = "--debug" ] && DEBUG=true

if ! docker compose ps mysql 2>/dev/null | grep -q "healthy"; then
  echo "→ MySQL not healthy — docker compose up -d"
  docker compose up -d
  until docker compose ps mysql 2>/dev/null | grep -q "healthy"; do
    sleep 1
  done
fi

echo "→ starting ngrok tunnel: omit-strainer-player.ngrok-free.dev → :8089"
ngrok start cleancars --log stdout --log-level warn > /tmp/opencode/ngrok-dev.log 2>&1 &
NGROK_PID=$!
trap 'kill "$NGROK_PID" 2>/dev/null' EXIT

echo "→ starting bootRun on :8089 (Ctrl-C stops both)"
echo "  webhook URL (set once in the Razorpay Dashboard):"
echo "    https://omit-strainer-player.ngrok-free.dev/api/webhooks/razorpay"
echo "  verify once the app is up: ./scripts/dev-status.sh"
if [ "$DEBUG" = "true" ]; then
  echo "→ DEBUG MODE: JDWP suspend=n on localhost:5005 — attach IntelliJ 'Attach to process'/'Remote JVM debug' there"
  ./gradlew bootRun -PdebugRun=true
else
  ./gradlew bootRun
fi
