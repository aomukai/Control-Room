#!/bin/bash
set -euo pipefail

PORT="${CR_PORT:-8080}"
BASE_URL="${CR_BASE_URL:-http://localhost:${PORT}}"
ISSUE_ID="${ISSUE_ID:-}"
MESSAGE="${MESSAGE:-}"
CHOICE="${CHOICE:-}"

if [ -z "${ISSUE_ID}" ]; then
  echo "ISSUE_ID is required."
  exit 1
fi

if [ -z "${MESSAGE}" ]; then
  echo "MESSAGE is required."
  exit 1
fi

PAYLOAD="$(python3 - <<'PY'
import json, os
issue_id = os.environ.get("ISSUE_ID")
message = os.environ.get("MESSAGE")
choice = os.environ.get("CHOICE")
payload = {"issueId": issue_id, "message": message}
if choice:
    payload["clarificationChoice"] = choice
print(json.dumps(payload))
PY
)"

curl -sS "${BASE_URL}/api/ai/playbook/scene" \
  -H "Content-Type: application/json" \
  -d "${PAYLOAD}"
echo ""
