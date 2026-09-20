#!/usr/bin/env bash
# Idempotent dev tunnel: make sure the pinned ngrok tunnel is up, never blocking.
# Safe as a Gradle "Before launch" step or to run alone:
#
#   ./scripts/dev-tunnel.sh
#
# Skips silently if the tunnel is already running (pinned domain in the global
# ngrok config: cleancars → omit-strainer-player.ngrok-free.dev).

set -euo pipefail

if pgrep -f "ngrok start cleancars" > /dev/null; then
  echo "→ ngrok tunnel omit-strainer-player.ngrok-free.dev already up"
  exit 0
fi

if ! command -v ngrok > /dev/null 2>&1; then
  echo "WARN: ngrok not installed — skipping tunnel (Razorpay webhooks won't reach :8089; install: brew install ngrok)" >&2
  exit 0   # soft: never block an IDE launch over a missing nicety
fi

mkdir -p /tmp/opencode
echo "→ opening ngrok tunnel omit-strainer-player.ngrok-free.dev → :8089"
nohup ngrok start cleancars --log stdout --log-level warn > /tmp/opencode/ngrok-dev.log 2>&1 &
echo "→ tunnel pid $! (survives this script; restart: pkill ngrok start cleancars)"
