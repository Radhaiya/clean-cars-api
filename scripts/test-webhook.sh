#!/usr/bin/env bash
# Fire a signed fake Razorpay webhook at the locally-running API (:8089 by default).
#
# Usage:
#   RAZORPAY_WEBHOOK_SECRET=<dashboard secret> ./scripts/test-webhook.sh <event_type> [payload_json] [port]
#
# Examples:
#   RAZORPAY_WEBHOOK_SECRET=test-webhook-secret ./scripts/test-webhook.sh subscription.activated
#   RAZORPAY_WEBHOOK_SECRET=test-webhook-secret ./scripts/test-webhook.sh subscription.charged \
#     '{"payload":{"subscription":{"entity":{"id":"sub_ABC","current_start":1750000000,"current_end":1752592000}},"payment":{"entity":{"id":"pay_ABC","amount":49900,"currency":"INR","status":"captured","created_at":1750000001}}}}'
#
# Re-running with the IDENTICAL payload is a duplicate delivery: the API answers
# 200 without reprocessing (x-razorpay-event-id defaults to a hash of the body).

set -euo pipefail

SECRET="${RAZORPAY_WEBHOOK_SECRET:?set RAZORPAY_WEBHOOK_SECRET (same as app.razorpay.webhook-secret)}"
EVENT="${1:?event type, e.g. subscription.activated}"
RAW="${2:-}"
PORT="${3:-8089}"
SUB_ID="${RAZORPAY_SUB_ID:-sub_test_local}"

# Default payloads for the two flows worth poking at: activation and a repeated charge.
if [ -z "$RAW" ]; then
  case "$EVENT" in
    subscription.activated|subscription.charged)
      RAW="{\"event\":\"${EVENT}\",\"contains\":[\"subscription\"],\"payload\":{\"subscription\":{\"entity\":{\"id\":\"${SUB_ID}\",\"status\":\"active\",\"current_start\":1750000000,\"current_end\":1752592000}},\"payment\":{\"entity\":{\"id\":\"pay_test_${RANDOM}\",\"amount\":49900,\"currency\":\"INR\",\"status\":\"captured\",\"created_at\":1750000001}}}}"
      ;;
    *)
      RAW="{\"event\":\"${EVENT}\",\"contains\":[\"subscription\"],\"payload\":{\"subscription\":{\"entity\":{\"id\":\"${SUB_ID}\"}}}}"
      ;;
  esac
fi

# Razorpay signs the *raw body bytes*; make sure we send exactly what we signed.
EVENT_ID=$(printf '%s' "$RAW" | shasum -a 256 | cut -d' ' -f1)
SIGNATURE=$(printf '%s' "$RAW" | openssl dgst -sha256 -hmac "$SECRET" -hex | awk '{print $NF}')

exec curl -s -i -X POST "http://localhost:${PORT}/api/webhooks/razorpay" \
  -H "Content-Type: application/json" \
  -H "X-Razorpay-Signature: ${SIGNATURE}" \
  -H "X-Razorpay-Event-Id: ${EVENT_ID}" \
  -d "$RAW"
