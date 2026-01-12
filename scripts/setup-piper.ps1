# Piper TTS Setup Script for Control Room
# Run this once from the Control-Room folder: .\scripts\setup-piper.ps1

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== Piper TTS Setup for Control Room ===" -ForegroundColor Cyan
Write-Host ""

# Create voices directory
$voicesDir = "data\voices"
if (-not (Test-Path $voicesDir)) {
    Write-Host "Creating $voicesDir directory..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Path $voicesDir -Force | Out-Null
}

# Voice configurations: name, language, gender
$voices = @(
    @{
        name = "en_US-amy-medium"
        lang = "en/en_US/amy/medium"
        display = "English Female (Amy)"
    },
    @{
        name = "en_US-ryan-medium"
        lang = "en/en_US/ryan/medium"
        display = "English Male (Ryan)"
    },
    @{
        name = "de_DE-thorsten-medium"
        lang = "de/de_DE/thorsten/medium"
        display = "German Male (Thorsten)"
    },
    @{
        name = "de_DE-ramona-low"
        lang = "de/de_DE/ramona/low"
        display = "German Female (Ramona)"
    }
)

$baseUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0"

Write-Host "Downloading 4 voice models (~210MB total)..." -ForegroundColor Yellow
Write-Host ""

foreach ($voice in $voices) {
    $onnxFile = "$voicesDir\$($voice.name).onnx"
    $jsonFile = "$voicesDir\$($voice.name).onnx.json"

    # Download .onnx model
    if (-not (Test-Path $onnxFile)) {
        Write-Host "  Downloading $($voice.display)..." -ForegroundColor White
        $url = "$baseUrl/$($voice.lang)/$($voice.name).onnx"
        try {
            Invoke-WebRequest -Uri $url -OutFile $onnxFile -UseBasicParsing
            Write-Host "    Model: OK" -ForegroundColor Green
        } catch {
            Write-Host "    Model: FAILED - $($_.Exception.Message)" -ForegroundColor Red
            continue
        }
    } else {
        Write-Host "  $($voice.display) model already exists, skipping..." -ForegroundColor Gray
    }

    # Download .onnx.json config
    if (-not (Test-Path $jsonFile)) {
        $url = "$baseUrl/$($voice.lang)/$($voice.name).onnx.json"
        try {
            Invoke-WebRequest -Uri $url -OutFile $jsonFile -UseBasicParsing
            Write-Host "    Config: OK" -ForegroundColor Green
        } catch {
            Write-Host "    Config: FAILED - $($_.Exception.Message)" -ForegroundColor Red
        }
    } else {
        Write-Host "    Config already exists, skipping..." -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "=== Voice Downloads Complete ===" -ForegroundColor Cyan
Write-Host ""

# Check if piper-tts is installed
Write-Host "Checking Python piper-tts installation..." -ForegroundColor Yellow
$piperInstalled = $false
try {
    $result = pip show piper-tts 2>&1
    if ($result -match "Name: piper-tts") {
        $piperInstalled = $true
        Write-Host "  piper-tts is already installed" -ForegroundColor Green
    }
} catch {}

if (-not $piperInstalled) {
    Write-Host "  Installing piper-tts[http]..." -ForegroundColor Yellow
    try {
        pip install "piper-tts[http]"
        Write-Host "  piper-tts installed successfully" -ForegroundColor Green
    } catch {
        Write-Host "  Failed to install piper-tts: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "  You may need to run: pip install piper-tts[http]" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "=== Setup Complete ===" -ForegroundColor Green
Write-Host ""
Write-Host "Voices installed in: $((Resolve-Path $voicesDir).Path)" -ForegroundColor White
Write-Host ""
Write-Host "To test manually, run:" -ForegroundColor White
Write-Host "  python -m piper.http_server --data-dir data/voices --port 5050" -ForegroundColor Cyan
Write-Host ""
Write-Host "Then test with:" -ForegroundColor White
Write-Host '  curl -X POST -H "Content-Type: application/json" -d "{\"text\":\"Hello world\", \"voice\":\"en_US-amy-medium\"}" http://localhost:5050 --output test.wav' -ForegroundColor Cyan
Write-Host ""
