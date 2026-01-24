#!/bin/bash
set +e

VOICE_PATH="data/voices/en_US-amy-medium.onnx"
PIPER_PORT=5050

if [ ! -f "$VOICE_PATH" ]; then
    echo "  [!] Piper voices not found. Run: scripts/setup-piper.ps1 (Windows) or install voices manually."
    exit 1
fi

port_listening() {
    if command -v ss >/dev/null 2>&1; then
        ss -ltn 2>/dev/null | grep -q ":${PIPER_PORT} "
        return $?
    fi
    if command -v netstat >/dev/null 2>&1; then
        netstat -an 2>/dev/null | grep -q ":${PIPER_PORT} .*LISTEN"
        return $?
    fi
    if command -v lsof >/dev/null 2>&1; then
        lsof -iTCP:${PIPER_PORT} -sTCP:LISTEN >/dev/null 2>&1
        return $?
    fi
    return 1
}

port_ready() {
    if command -v nc >/dev/null 2>&1; then
        nc -z 127.0.0.1 ${PIPER_PORT} >/dev/null 2>&1
        return $?
    fi
    if (echo > /dev/tcp/127.0.0.1/${PIPER_PORT}) >/dev/null 2>&1; then
        return 0
    fi
    return 1
}

if port_listening; then
    echo "  [OK] Piper TTS already running on port ${PIPER_PORT}"
    exit 0
fi

echo "  [*] Starting Piper TTS server on port ${PIPER_PORT}..."
python -m piper.http_server -m "${VOICE_PATH}" --data-dir data/voices --port ${PIPER_PORT} >/dev/null 2>&1 &

PIPER_READY=0
for i in $(seq 1 15); do
    if port_ready; then
        PIPER_READY=1
        break
    fi
    sleep 1
done

if [ $PIPER_READY -eq 1 ]; then
    echo "  [OK] Piper TTS server started"
else
    echo "  [!] Piper is still starting; continuing without waiting."
fi

exit 0
