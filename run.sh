#!/bin/bash
set +e

echo ""
echo "  =========================================="
echo "     Control Room - Development Mode"
echo "  =========================================="
echo ""

WRAPPER_PATH="gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://github.com/gradle/gradle/raw/v8.5.0/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_PATH" ]; then
    echo "  [*] Downloading Gradle wrapper..."
    mkdir -p "$(dirname "$WRAPPER_PATH")"
    if command -v curl >/dev/null 2>&1; then
        curl -L -o "$WRAPPER_PATH" "$WRAPPER_URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$WRAPPER_PATH" "$WRAPPER_URL"
    else
        echo ""
        echo "  [ERROR] curl or wget is required to download Gradle wrapper."
        exit 1
    fi

    if [ ! -f "$WRAPPER_PATH" ]; then
        echo ""
        echo "  [ERROR] Failed to download Gradle wrapper."
        echo "          Please ensure you have internet access."
        exit 1
    fi
    echo "  [OK] Gradle wrapper downloaded."
    echo ""
fi

chmod +x gradlew

# Start Piper TTS server if voices are installed
if [ -f "data/voices/en_US-amy-medium.onnx" ]; then
    if [ -f "scripts/start-piper.sh" ]; then
        bash scripts/start-piper.sh
        echo ""
    else
        echo "  [!] Piper voices found but scripts/start-piper.sh is missing."
        echo "      Skipping Piper startup."
        echo ""
    fi
fi

echo "  [*] Building and starting server..."
echo "  [*] Browser will open automatically when ready."
echo ""
echo "  ------------------------------------------"
echo ""

./gradlew run "$@"
EXIT_CODE=$?

echo ""
echo "  ------------------------------------------"

if [ $EXIT_CODE -ne 0 ]; then
    echo ""
    echo "  =========================================="
    echo "     [ERROR] Exit code: $EXIT_CODE"
    echo "  =========================================="
    echo ""
    echo "  Common issues:"
    echo "  - Java 17+ JDK not installed or not in PATH"
    echo "  - Port 8080 in use (will auto-select another)"
    echo "  - Network/firewall blocking connection"
    echo ""
else
    echo ""
    echo "  [OK] Server stopped gracefully."
    echo ""
fi

if [ -t 0 ]; then
    read -n 1 -s -r -p "  Press any key to close..."
    echo ""
fi

exit $EXIT_CODE
