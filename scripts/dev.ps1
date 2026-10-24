<#
.SYNOPSIS
    Starts BidMyHobby services natively on Windows without Docker Desktop.

.DESCRIPTION
    Launches Catalog, Auction, Hobby-AI, Dev Gateway, and Next.js Frontend.
    Supports running all in one terminal or in separate PowerShell windows.

.PARAMETER SeparateWindows
    Spawns each service in a dedicated Windows PowerShell window.

.PARAMETER Services
    List of services to start (e.g., -Services catalog,frontend,gateway).
    Defaults to all: gateway, catalog, auction, hobby-ai, frontend.

.PARAMETER Stop
    Kills any processes listening on BidMyHobby ports (8080, 8081, 8082, 8088, 3000).

.EXAMPLE
    .\scripts\dev.ps1
    .\scripts\dev.ps1 -SeparateWindows
    .\scripts\dev.ps1 -Services gateway,frontend
    .\scripts\dev.ps1 -Stop
#>

[CmdletBinding()]
param(
    [switch]$SeparateWindows,
    [string[]]$Services = @('gateway', 'catalog', 'auction', 'hobby-ai', 'frontend'),
    [switch]$Stop
)

$RepoRoot = (Get-Item $PSScriptRoot).Parent.FullName
$Ports = @{
    gateway = 8080
    catalog = 8081
    auction = 8082
    'hobby-ai' = 8088
    frontend = 3000
}

# 1. Stop command handler
if ($Stop) {
    Write-Host "Stopping all BidMyHobby services..." -ForegroundColor Yellow
    foreach ($entry in $Ports.GetEnumerator()) {
        $port = $entry.Value
        try {
            $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
            if ($conns) {
                foreach ($c in $conns) {
                    $pidToKill = $c.OwningProcess
                    if ($pidToKill -gt 0) {
                        Write-Host "  Killing process $pidToKill listening on port $port ($($entry.Key))..." -ForegroundColor DarkYellow
                        Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
                    }
                }
            }
        } catch {
            # Ignore errors
        }
    }
    Write-Host "All specified port listeners stopped." -ForegroundColor Green
    return
}

# 2. Check and configure Java 17+
$jdkPath = $env:JAVA_HOME
if (-not $jdkPath -or -not (Test-Path "$jdkPath\bin\javac.exe")) {
    $candidates = @(
        "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot",
        "C:\Users\server\.jdks\openjdk-19.0.2",
        "$env:USERPROFILE\.jdks\openjdk-19.0.2"
    )
    foreach ($cand in $candidates) {
        if (Test-Path "$cand\bin\javac.exe") {
            $jdkPath = $cand
            break
        }
    }
}

if ($jdkPath -and (Test-Path "$jdkPath\bin\javac.exe")) {
    $env:JAVA_HOME = $jdkPath
    $env:PATH = "$jdkPath\bin;$env:PATH"
    Write-Host "[Environment] JAVA_HOME set to: $jdkPath" -ForegroundColor Cyan
} else {
    Write-Warning "Could not find a JDK with javac.exe. Spring Boot compilation may fail if JAVA_HOME is not set."
}

# 3. Check and configure Node.js
$nodeCandidates = @(
    "C:\Users\server\AppData\Local\Programs\nodejs",
    "$env:LOCALAPPDATA\Programs\nodejs",
    "C:\Program Files\nodejs"
)
$nodePath = ""
foreach ($nc in $nodeCandidates) {
    if (Test-Path "$nc\node.exe") {
        $nodePath = $nc
        $env:PATH = "$nc;$env:PATH"
        Write-Host "[Environment] Node.js configured from: $nc" -ForegroundColor Cyan
        break
    }
}

# 4. Load .env file into environment
$envFile = Join-Path $RepoRoot ".env"
if (Test-Path $envFile) {
    Write-Host "[Environment] Loading variables from $envFile" -ForegroundColor Cyan
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $key, $val = $line -split "=", 2
            $key = $key.Trim()
            $val = $val.Trim()
            if ($key) {
                [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
            }
        }
    }
}

# Safe defaults if missing
if (-not $env:AUTH_TOKEN_SECRET) {
    $env:AUTH_TOKEN_SECRET = "local-development-secret-change-me-123456789"
}
if (-not $env:CATALOG_SERVICE_URL) {
    $env:CATALOG_SERVICE_URL = "http://localhost:8081"
}
if (-not $env:APP_SITE_URL) {
    $env:APP_SITE_URL = "http://localhost:3000"
}
if (-not $env:SITE_URL) {
    $env:SITE_URL = "http://localhost:8080"
}

# 4. Service definition dictionary
$ServiceDefs = @{
    gateway = @{
        Name = "Dev Gateway (:8080)"
        Command = "node scripts/dev-gateway.mjs"
        Cwd = $RepoRoot
        Color = "Green"
    }
    catalog = @{
        Name = "Catalog Service (:8081)"
        Command = ".\mvnw.cmd spring-boot:run"
        Cwd = Join-Path $RepoRoot "services\catalog"
        Color = "Cyan"
    }
    auction = @{
        Name = "Auction Service (:8082)"
        Command = ".\mvnw.cmd spring-boot:run"
        Cwd = Join-Path $RepoRoot "services\auction"
        Color = "Magenta"
    }
    'hobby-ai' = @{
        Name = "Hobby-AI Service (:8088)"
        Command = ".\mvnw.cmd spring-boot:run"
        Cwd = Join-Path $RepoRoot "services\hobby-ai"
        Color = "Yellow"
    }
    frontend = @{
        Name = "Next.js Frontend (:3000)"
        Command = "npm run dev"
        Cwd = Join-Path $RepoRoot "apps\web"
        Color = "Blue"
    }
}

# 5. Launch in separate windows if requested
if ($SeparateWindows) {
    Write-Host "Launching services in separate PowerShell windows..." -ForegroundColor Green
    foreach ($svcKey in $Services) {
        if (-not $ServiceDefs.ContainsKey($svcKey)) {
            Write-Warning "Unknown service '$svcKey', skipping."
            continue
        }
        $def = $ServiceDefs[$svcKey]
        $title = "BidMyHobby - $($def.Name)"
        $cwd = $def.Cwd
        $cmd = $def.Command
        $scriptBlock = @"
`$host.UI.RawUI.WindowTitle = '$title'
cd '$cwd'
`$env:JAVA_HOME = '$($env:JAVA_HOME)'
`$env:PATH = '$($env:JAVA_HOME)\bin;' + '$nodePath;' + `$env:PATH
Write-Host '=========================================' -ForegroundColor $($def.Color)
Write-Host 'Starting $($def.Name)...' -ForegroundColor $($def.Color)
Write-Host 'Directory: $cwd'
Write-Host 'Command:   $cmd'
Write-Host '=========================================' -ForegroundColor $($def.Color)
$cmd
"@
        Start-Process powershell -ArgumentList "-NoExit", "-Command", $scriptBlock
        Start-Sleep -Milliseconds 800
    }
    Write-Host "`nAll selected services have been launched in separate windows." -ForegroundColor Green
    Write-Host "Use '.\scripts\dev.ps1 -Stop' to terminate them anytime." -ForegroundColor Cyan
    return
}

# 6. Default: Concurrent launch in current console
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Starting BidMyHobby Local Native Development Stack       " -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Gateway:  http://localhost:8080"
Write-Host "  Frontend: http://localhost:3000"
Write-Host "  Catalog:  http://localhost:8081"
Write-Host "  Auction:  http://localhost:8082"
Write-Host "  Hobby AI: http://localhost:8088"
Write-Host "`nPress Ctrl+C to stop all services." -ForegroundColor Yellow
Write-Host "============================================================`n"

$jobs = @{}
try {
    foreach ($svcKey in $Services) {
        if (-not $ServiceDefs.ContainsKey($svcKey)) { continue }
        $def = $ServiceDefs[$svcKey]
        $cwd = $def.Cwd
        $cmd = $def.Command
        $jh = $env:JAVA_HOME
        $np = $nodePath

        $job = Start-Job -ScriptBlock {
            param($c, $command, $javaHome, $npPath)
            Set-Location $c
            if ($javaHome) {
                $env:JAVA_HOME = $javaHome
                $env:PATH = "$javaHome\bin;$env:PATH"
            }
            if ($npPath) {
                $env:PATH = "$npPath;$env:PATH"
            }
            Invoke-Expression $command
        } -ArgumentList $cwd, $cmd, $jh, $np

        $jobs[$svcKey] = @{
            Job = $job
            Def = $def
        }
        Write-Host "Started $($def.Name)..." -ForegroundColor $def.Color
    }

    # Stream logs from all background jobs
    while ($true) {
        $anyRunning = $false
        foreach ($entry in $jobs.GetEnumerator()) {
            $jobObj = $entry.Value.Job
            $def = $entry.Value.Def
            if ($jobObj.State -eq 'Running') {
                $anyRunning = $true
            }
            $output = Receive-Job -Job $jobObj -ErrorAction SilentlyContinue
            if ($output) {
                foreach ($line in $output) {
                    Write-Host "[$($entry.Key)] $line" -ForegroundColor $def.Color
                }
            }
            if ($jobObj.State -eq 'Failed') {
                $errs = $jobObj.ChildJobs | ForEach-Object { $_.JobStateInfo.Reason.Message }
                foreach ($err in $errs) {
                    if ($err) { Write-Host "[$($entry.Key)] ERROR: $err" -ForegroundColor Red }
                }
            }
        }
        if (-not $anyRunning) {
            Write-Host "All service jobs have completed." -ForegroundColor Yellow
            break
        }
        Start-Sleep -Milliseconds 250
    }
} finally {
    Write-Host "`nStopping background service jobs..." -ForegroundColor Yellow
    foreach ($entry in $jobs.GetEnumerator()) {
        Stop-Job -Job $entry.Value.Job -ErrorAction SilentlyContinue
        Remove-Job -Job $entry.Value.Job -Force -ErrorAction SilentlyContinue
    }
    # Free up the ports
    foreach ($entry in $Ports.GetEnumerator()) {
        $port = $entry.Value
        $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        if ($conns) {
            foreach ($c in $conns) {
                if ($c.OwningProcess -gt 0) {
                    Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue
                }
            }
        }
    }
    Write-Host "Services stopped." -ForegroundColor Green
}
