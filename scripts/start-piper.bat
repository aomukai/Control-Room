@echo off
setlocal enabledelayedexpansion
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

REM Wait for Piper to initialize (poll port readiness up to ~15 seconds)
set "PIPER_READY="
for /l %%i in (1,1,15) do (
    for /f %%s in ('powershell -NoProfile -Command "(Test-NetConnection -ComputerName 127.0.0.1 -Port 5050).TcpTestSucceeded"') do (
        set "PIPER_READY=%%s"
    )
    if /i "!PIPER_READY!"=="True" goto :piper_ready
    timeout /t 1 /nobreak >nul
)

:piper_ready
if /i "%PIPER_READY%"=="True" (
    echo  [OK] Piper TTS server started
    exit /b 0
) else (
    echo  [!] Piper is still starting; continuing without waiting.
    exit /b 0
)
