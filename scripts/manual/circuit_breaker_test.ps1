param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

Write-Host "Running circuit breaker manual test against $BaseUrl"

function Invoke-Api($Method, $Path, $Body) {
    $uri = "$BaseUrl$Path"
    if ($Body -ne $null) {
        return Invoke-RestMethod -Method $Method -Uri $uri -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 6) -ErrorAction Stop
    }
    return Invoke-RestMethod -Method $Method -Uri $uri -ErrorAction Stop
}

try {
    Invoke-Api "GET" "/api/issues" $null | Out-Null
} catch {
    Write-Host "Server not reachable at $BaseUrl. Start the app and re-run."
    exit 1
}

# 1) Create issue
$issue = Invoke-Api "POST" "/api/issues" @{
    title = "Circuit breaker test"
    body = "Testing comment validation and freeze flow."
    openedBy = "system"
    assignedTo = "moderator"
    tags = @("meta", "test")
    priority = "normal"
}

Write-Host "Created issue #" $issue.id

# 2) Attempt a short comment (should reject)
$shortBody = "Too short."
try {
    Invoke-Api "POST" "/api/issues/$($issue.id)/comments" @{
        author = "critic"
        body = $shortBody
        impactLevel = "structural"
    } | Out-Null
    Write-Host "Unexpected: short comment accepted."
} catch {
    Write-Host "Expected rejection for short comment."
}

# 3) Attempt escalation language (should freeze)
$escalation = "CRITICAL: We MUST change this immediately. This is an EMERGENCY."
$filler = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma tau"
try {
    Invoke-Api "POST" "/api/issues/$($issue.id)/comments" @{
        author = "critic"
        body = ($escalation + " " + $filler + " " + ("extra words " * 10))
        impactLevel = "structural"
        evidence = @{
            issues = @($issue.id)
        }
    } | Out-Null
    Write-Host "Unexpected: escalation comment accepted."
} catch {
    $status = $null
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
        $status = $_.Exception.Response.StatusCode.value__
    }
    if ($status -eq 409) {
        Write-Host "Expected freeze triggered (409)."
    } elseif ($status -eq 400) {
        Write-Host "Expected rejection triggered (400)."
    } else {
        Write-Host "Expected freeze or rejection for escalation language."
    }
}

# 4) Fetch issue to inspect frozen status
$frozen = Invoke-Api "GET" "/api/issues/$($issue.id)"
Write-Host "Issue status:" $frozen.status
Write-Host "Frozen reason:" $frozen.frozenReason
Write-Host "Frozen until:" $frozen.frozenUntil

Write-Host "Done."
