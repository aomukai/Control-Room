@echo off
setlocal enabledelayedexpansion

title Control Room (Development)

echo.
echo  ==========================================
echo     Control Room - Development Mode
echo  ==========================================
echo.

REM Check if gradle-wrapper.jar exists
if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo  [*] Downloading Gradle wrapper...
    mkdir "gradle\wrapper" 2>nul
    powershell -Command "Invoke-WebRequest -Uri 'https://github.com/gradle/gradle/raw/v8.5.0/gradle/wrapper/gradle-wrapper.jar' -OutFile 'gradle\wrapper\gradle-wrapper.jar'" 2>nul
    if errorlevel 1 (
        echo  [!] PowerShell download failed, trying curl...
        curl -L -o "gradle\wrapper\gradle-wrapper.jar" "https://github.com/gradle/gradle/raw/v8.5.0/gradle/wrapper/gradle-wrapper.jar" 2>nul
    )
    if not exist "gradle\wrapper\gradle-wrapper.jar" (
        echo.
        echo  [ERROR] Failed to download Gradle wrapper.
        echo          Please ensure you have internet access.
        goto :error
    )
    echo  [OK] Gradle wrapper downloaded.
    echo.
)

REM Start Piper TTS server if voices are installed
if exist "data\voices\en_US-amy-medium.onnx" (
    call scripts\start-piper.bat
    echo.
)

echo  [*] Building and starting server...
echo  [*] Browser will open automatically when ready.
echo.
echo  ------------------------------------------
echo.

REM Run the application in dev mode (--dev is added by build.gradle)
set "EXTRA_ARGS="
echo %* | findstr /I /C:"--workspace-root" >nul
if errorlevel 1 set "EXTRA_ARGS=--args=--workspace-root=\"%CD%\\workspace\""
call gradlew.bat run %EXTRA_ARGS% %*
set EXIT_CODE=%ERRORLEVEL%

echo.
echo  ------------------------------------------

if %EXIT_CODE% NEQ 0 (
    goto :error
)

echo.
echo  [OK] Server stopped gracefully.
echo.
goto :end

:error
echo.
echo  ==========================================
echo     [ERROR] Exit code: %EXIT_CODE%
echo  ==========================================
echo.
echo  Common issues:
echo  - Java 17+ JDK not installed or not in PATH
echo  - Port 8080 in use (will auto-select another)
echo  - Network/firewall blocking connection
echo.
echo  Press any key to close this window...
pause >nul
exit /b %EXIT_CODE%

:end
echo  Press any key to close this window...
pause >nul
exit /b 0
