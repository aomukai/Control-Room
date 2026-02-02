#!/bin/bash
set -euo pipefail

PORT="${CR_PORT:-8080}"
BASE_URL="${CR_BASE_URL:-http://localhost:${PORT}}"
CHOICE="${CR_CHOICE:-Outline order (default)}"

curl -sS "${BASE_URL}/api/ai/chief/route" \
  -H "Content-Type: application/json" \
  -d "{
    \"issueId\": \"manual-test-issue\",
    \"message\": \"let's do scene 3\",
    \"clarificationChoice\": \"${CHOICE}\"
  }"
echo ""
