#!/usr/bin/env bash
# Post-startup checklist after running ./scripts/dev-up.sh (or when resuming after a shutdown).
# Every step should print OK; a FAIL tells you exactly what to fix.
#
#   ./scripts/dev-status.sh

set -euo pipefail
cd "$(dirname "$0")/.."

ok()   { printf "  \033[32mOK\033[0m   %s\n" "$1"; }
fail() { printf "  \033[31mFAIL\033[0m %s\n" "$1"; }

printf "\ndev-status: %s\n\n" "$(date '+%X')"

# 1. Database (docker compose)
if docker compose ps mysql 2>/dev/null | grep -q "healthy"; then
  ok "MySQL   — cleancars-mysql healthy on :3370"
else
  fail "MySQL   — container not healthy. Fix: docker compose up -d"
fi

# 2. App on :8089 (401 = up and demanding auth / hmac — exactly right)
APP_CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost:8089/api/plans || true)
if [ "$APP_CODE" = "401" ] || [ "$APP_CODE" = "403" ]; then
  ok "App     — :8089 up (auth gate answering)"
elif [ "$APP_CODE" = "000" ]; then
  fail "App     — nothing listening on :8089. Fix: ./scripts/dev-up.sh"
else
  fail "App     — unexpected HTTP $APP_CODE from :8089"
fi

# 3. The pinned ngrok domain, end to end through the internet -> app
TUNNEL_CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 \
  -X POST https://omit-strainer-player.ngrok-free.dev/api/webhooks/razorpay \
  -H "X-Razorpay-Signature: invalid" --data '{}' || true)
if [ "$TUNNEL_CODE" = "401" ]; then
  ok "Tunnel  — omit-strainer-player.ngrok-free.dev → :8089 (signature gate answering)"
elif [ "$TUNNEL_CODE" = "000" ]; then
  fail "Tunnel  — domain unreachable. Fix: ngrok start cleancars (or run scripts/dev-up.sh again)"
else
  fail "Tunnel  — unexpected HTTP $TUNNEL_CODE through the tunnel"
fi

# 4. Razorpay keys present (reads scripts/.dev.env if the app was started with it)
if [ -f "scripts/.dev.env" ] && grep -q "RAZORPAY_KEY_ID=.[a-zA-Z]" scripts/.dev.env 2>/dev/null; then
  ok "Keys    — scripts/.dev.env has RAZORPAY_KEY_ID etc. (verify they're TEST keys)"
else
  fail "Keys    — scripts/.dev.env missing or empty; subscribe/plans will fail"
fi

echo
echo "Webhook URL (unchanged, set once in the Razorpay Dashboard):"
echo "  https://omit-strainer-player.ngrok-free.dev/api/webhooks/razorpay"
echo "Signed fake event (needs the app's RAZORPAY_WEBHOOK_SECRET):"
echo "  RAZORPAY_WEBHOOK_SECRET=... ./scripts/test-webhook.sh subscription.activated"
