@echo off
REM Start Piper TTS server in background
REM Called by run.bat - not meant to be run directly

REM Check if voices exist
if not exist "data\voices\en_US-amy-medium.onnx" (
    echo  [!] Piper voices not found. Run: .\scripts\setup-piper.ps1
    exit /b 1
)

REM Check if Piper is already running on port 5050
netstat -an | findstr ":5050.*LISTENING" >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo  [OK] Piper TTS already running on port 5050
    exit /b 0
)

REM Start Piper HTTP server in background (requires default model + data-dir for voice switching)
echo  [*] Starting Piper TTS server on port 5050...
start /B /MIN "Piper TTS" python -m piper.http_server -m data\voices\en_US-amy-medium.onnx --data-dir data\voices --port 5050 >nul 2>&1

REM Wait for Piper to initialize (Python + ONNX model loading takes a few seconds)
timeout /t 4 /nobreak >nul
netstat -an | findstr ":5050.*LISTENING" >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo  [OK] Piper TTS server started
    exit /b 0
)

REM Give it one more chance with a longer wait
timeout /t 3 /nobreak >nul
netstat -an | findstr ":5050.*LISTENING" >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo  [OK] Piper TTS server started (slow init)
    exit /b 0
) else (
    echo  [!] Piper TTS failed to start (is piper-tts installed?)
    exit /b 1
)
