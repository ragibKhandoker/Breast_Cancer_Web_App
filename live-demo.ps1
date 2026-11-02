# Live demo: puts the whole site (frontend + backend + AI) on a free public https address through a Cloudflare
# quick tunnel. No account, no server and no cloud storage: everything runs on this laptop, with the real database
# from Backend/.env (MongoDB Atlas). The site is online while this window is open and the laptop is awake.
#
#   Double-click live-demo.cmd, or:  powershell -ExecutionPolicy Bypass -File live-demo.ps1
#
# Each run gets a new random address (https://<words>.trycloudflare.com). Close the window or press Enter to stop.
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$port = 8090   # not 8081, so the normal development backend can keep running
$logs = Join-Path $env:TEMP 'breastcare-live-demo'
New-Item -ItemType Directory -Force $logs | Out-Null

function Stop-Tree($proc) {
    if ($proc -and -not $proc.HasExited) { taskkill /T /F /PID $proc.Id 2>$null | Out-Null }
}

$cloudflared = (Get-Command cloudflared -ErrorAction SilentlyContinue).Source
if (-not $cloudflared) { $cloudflared = 'C:\Program Files (x86)\cloudflared\cloudflared.exe' }
if (-not (Test-Path $cloudflared)) {
    throw 'cloudflared is not installed. Run:  winget install --id Cloudflare.cloudflared -e'
}
if (-not (Test-Path (Join-Path $root 'Backend\.env'))) { throw 'Backend\.env is missing (MONGODB_URI, JWT_SECRET).' }
if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
    throw "Port $port is already in use. Close the other live demo first."
}

Write-Host '1/4 Building the website...' -ForegroundColor Cyan
Push-Location (Join-Path $root 'Frontend')
try {
    if (-not (Test-Path node_modules)) { npm ci; if ($LASTEXITCODE) { throw 'npm ci failed' } }
    npm run build; if ($LASTEXITCODE) { throw 'Website build failed' }
} finally { Pop-Location }

$tunnel = $null; $backend = $null
try {
    Write-Host '2/4 Opening the public address...' -ForegroundColor Cyan
    $tunnelLog = Join-Path $logs 'tunnel.log'
    Remove-Item $tunnelLog -ErrorAction SilentlyContinue
    $tunnel = Start-Process $cloudflared -ArgumentList "tunnel --no-autoupdate --url http://localhost:$port" `
        -RedirectStandardError $tunnelLog -RedirectStandardOutput (Join-Path $logs 'tunnel.out') -WindowStyle Hidden -PassThru
    $url = $null
    for ($i = 0; $i -lt 90 -and -not $url; $i++) {
        Start-Sleep -Seconds 1
        if (Test-Path $tunnelLog) {
            $m = Select-String -Path $tunnelLog -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' | Select-Object -First 1
            if ($m) { $url = $m.Matches[0].Value }
        }
        if ($tunnel.HasExited) { break }
    }
    if (-not $url) { throw "Could not open the tunnel. See $tunnelLog" }

    Write-Host '3/4 Starting the server and the AI models (about 1-2 minutes)...' -ForegroundColor Cyan
    # Settings for this run only; Backend\.env still provides MONGODB_URI and JWT_SECRET.
    $env:SERVER_PORT = "$port"
    $env:CORS_ORIGINS = "$url,http://localhost:$port"
    $env:AUTH_COOKIE_SECURE = 'true'   # the public address is https
    $backendLog = Join-Path $logs 'backend.log'
    $backend = Start-Process (Join-Path $root 'Backend\mvnw.cmd') -ArgumentList '-q spring-boot:run' `
        -WorkingDirectory (Join-Path $root 'Backend') -RedirectStandardOutput $backendLog `
        -RedirectStandardError (Join-Path $logs 'backend.err') -WindowStyle Hidden -PassThru
    $up = $false
    for ($i = 0; $i -lt 300 -and -not $up; $i++) {
        Start-Sleep -Seconds 2
        if ($backend.HasExited) { break }
        try { $up = (Invoke-WebRequest "http://localhost:$port/actuator/health" -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200 } catch {}
    }
    if (-not $up) { throw "The server did not start. See $backendLog" }

    Write-Host '4/4 Checking the public address...' -ForegroundColor Cyan
    $ok = $false
    for ($i = 0; $i -lt 30 -and -not $ok; $i++) {
        try { $ok = (Invoke-WebRequest "$url/actuator/health" -UseBasicParsing -TimeoutSec 10).StatusCode -eq 200 } catch { Start-Sleep -Seconds 2 }
    }
    if (-not $ok) { Write-Host 'The public address is not answering yet; give it a minute.' -ForegroundColor Yellow }

    Write-Host ''
    Write-Host '  BreastCare is live:' -ForegroundColor Green
    Write-Host "  $url" -ForegroundColor Green
    Write-Host ''
    Write-Host '  Share this link. It works while this window stays open and the laptop is awake.'
    Set-Clipboard -Value $url -ErrorAction SilentlyContinue
    Write-Host '  (The link is copied to the clipboard.)'
    Write-Host ''
    Read-Host '  Press Enter to stop the live demo'
} finally {
    Write-Host 'Stopping...'
    Stop-Tree $backend
    Stop-Tree $tunnel
}
