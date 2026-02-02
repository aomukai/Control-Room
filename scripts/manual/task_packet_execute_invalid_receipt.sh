#!/bin/bash
set -euo pipefail

PORT="${CR_PORT:-8080}"
BASE_URL="${CR_BASE_URL:-http://localhost:${PORT}}"
PACKET_FILE="${PACKET_FILE:-}"
AGENT_ID="${AGENT_ID:-}"

if [ -z "${PACKET_FILE}" ]; then
  echo "PACKET_FILE is required (path to task packet JSON)."
  exit 1
fi

if [ ! -f "${PACKET_FILE}" ]; then
  echo "Packet file not found: ${PACKET_FILE}"
  exit 1
fi

if [ -z "${AGENT_ID}" ]; then
  echo "AGENT_ID is required (agent id to execute the packet)."
  exit 1
fi

PYTHON_BIN="${PYTHON_BIN:-python3}"
PAYLOAD="$(${PYTHON_BIN} - <<'PY'
import json, os
packet_file = os.environ.get("PACKET_FILE")
agent_id = os.environ.get("AGENT_ID")
with open(packet_file, "r", encoding="utf-8") as f:
    packet = json.load(f)
payload = {
    "agentId": agent_id,
    "packet": packet,
    "simulateInvalidReceipt": True
}
print(json.dumps(payload))
PY
)"

curl -sS "${BASE_URL}/api/ai/task/execute" \
  -H "Content-Type: application/json" \
  -d "${PAYLOAD}"
echo ""
