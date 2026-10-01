# Verificacion de la Fase 1 de Lebane (Windows PowerShell 5.1+).
# Uso, desde la raiz del repo:
#   powershell -ExecutionPolicy Bypass -File .\verificar-fase1.ps1
# Opcional: -SkipBackend -SkipFrontend -SkipCompose -SkipObservability
# Deja un log por paso y un resumen en .\verificacion\ (ignorado por git).

param(
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$SkipCompose,
    [switch]$SkipObservability
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
$logDir = Join-Path $root 'verificacion'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$summary = Join-Path $logDir 'resumen.txt'
"Verificacion Fase 1 - $(Get-Date -Format s)" | Set-Content -Encoding UTF8 $summary

function Write-Result([string]$name, [bool]$ok, [string]$detail = '') {
    $status = if ($ok) { 'OK  ' } else { 'FAIL' }
    $line = "[$status] $name $detail"
    Add-Content -Encoding UTF8 $summary $line
    if ($ok) { Write-Host $line -ForegroundColor Green } else { Write-Host $line -ForegroundColor Red }
}

# Ejecuta un comando via cmd.exe y guarda stdout+stderr en verificacion\<name>.log
function Invoke-Step([string]$name, [string]$command, [string]$workdir = $root) {
    $log = Join-Path $logDir "$name.log"
    Write-Host "==> $name : $command" -ForegroundColor Cyan
    Push-Location $workdir
    cmd /c "$command > `"$log`" 2>&1"
    $code = $LASTEXITCODE
    Pop-Location
    Write-Result $name ($code -eq 0) "(exit $code, log: verificacion\$name.log)"
    return ($code -eq 0)
}

function Get-HttpStatus([string]$url) {
    try {
        $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 10
        # PS 5.1 devuelve byte[] para content-types no reconocidos como texto (application/vnd.spring-boot.actuator.v3+json)
        $body = if ($r.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($r.Content) } else { $r.Content }
        return @{ Code = [int]$r.StatusCode; Body = $body }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $body = ''
            try { $body = (New-Object System.IO.StreamReader($resp.GetResponseStream())).ReadToEnd() } catch {}
            return @{ Code = [int]$resp.StatusCode; Body = $body }
        }
        return @{ Code = 0; Body = $_.Exception.Message }
    }
}

function Wait-HttpStatus([string]$url, [int]$expected, [int]$timeoutSec) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    do {
        $r = Get-HttpStatus $url
        if ($r.Code -eq $expected) { return $r }
        Start-Sleep -Seconds 5
    } while ((Get-Date) -lt $deadline)
    return $r
}

function Read-DotEnv([string]$path) {
    $map = @{}
    foreach ($line in Get-Content $path) {
        if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
        $k, $v = $line -split '=', 2
        $map[$k.Trim()] = $v.Trim()
    }
    return $map
}

# ---------- Prerrequisitos ----------
Write-Host "==> Prerrequisitos" -ForegroundColor Cyan
# Preferencia: Maven Wrapper del repo; luego mvn del PATH; luego el Maven embebido de IntelliJ.
$mvn = Join-Path $root 'backend\mvnw.cmd'
if (-not (Test-Path $mvn)) { $mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source }
if (-not $mvn) {
    $bundled = Get-ChildItem 'C:\Program Files\JetBrains' -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'plugins\maven\lib\maven3\bin\mvn.cmd' } |
        Where-Object { Test-Path $_ } | Select-Object -First 1
    $mvn = $bundled
}
Write-Result 'maven-disponible' ([bool]$mvn) "$mvn"

function Test-Jdk21([string]$home_) {
    if (-not $home_ -or -not (Test-Path (Join-Path $home_ 'bin\java.exe'))) { return $false }
    return (((cmd /c "`"$home_\bin\java.exe`" -version 2>&1") -join ' ') -match 'version "21')
}
# JAVA_HOME puede existir pero apuntar a otra versión: se busca un JDK 21 en las ubicaciones habituales.
if (-not (Test-Jdk21 $env:JAVA_HOME)) {
    $candidates = @((Join-Path $env:USERPROFILE '.jdks'), 'C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Microsoft', 'C:\Program Files\Amazon Corretto', 'C:\Program Files\Zulu') |
        ForEach-Object { Get-ChildItem $_ -Directory -ErrorAction SilentlyContinue } |
        Where-Object { $_.Name -match '21' -and (Test-Jdk21 $_.FullName) } | Select-Object -First 1
    if ($candidates) { $env:JAVA_HOME = $candidates.FullName }
}
$javaVersion = ''
if ($env:JAVA_HOME) { $javaVersion = (cmd /c "`"$env:JAVA_HOME\bin\java.exe`" -version 2>&1") -join ' ' }
Write-Result 'jdk-21' ($javaVersion -match 'version "21') "JAVA_HOME=$env:JAVA_HOME"

$node = (Get-Command node -ErrorAction SilentlyContinue).Source
Write-Result 'node-disponible' ([bool]$node) "$node"
$dockerOk = $false
if (Get-Command docker -ErrorAction SilentlyContinue) {
    cmd /c "docker info > nul 2>&1"
    $dockerOk = ($LASTEXITCODE -eq 0)
}
Write-Result 'docker-engine' $dockerOk

# ---------- 1. Backend ----------
if (-not $SkipBackend -and $mvn) {
    Invoke-Step 'backend-mvn-verify' "`"$mvn`" -B -ntp verify" (Join-Path $root 'backend') | Out-Null
    Get-ChildItem (Join-Path $root 'backend\target\surefire-reports'), (Join-Path $root 'backend\target\failsafe-reports') `
        -Filter '*.txt' -ErrorAction SilentlyContinue | ForEach-Object {
            Add-Content -Encoding UTF8 $summary ("    " + ((Get-Content $_.FullName) -match 'Tests run' | Select-Object -First 1))
        }
}

# ---------- 2. Frontend ----------
if (-not $SkipFrontend -and $node) {
    $fe = Join-Path $root 'frontend'
    if (Invoke-Step 'frontend-npm-install' 'npm install --no-audit --no-fund' $fe) {
        Invoke-Step 'frontend-typecheck' 'npm run typecheck' $fe | Out-Null
        Invoke-Step 'frontend-lint' 'npm run lint' $fe | Out-Null
        Invoke-Step 'frontend-test' 'npm test' $fe | Out-Null
        Invoke-Step 'frontend-build' 'npm run build' $fe | Out-Null
    }
}

# ---------- 3. Docker Compose ----------
if (-not $SkipCompose -and $dockerOk) {
    if (-not (Test-Path (Join-Path $root '.env'))) {
        Copy-Item (Join-Path $root '.env.example') (Join-Path $root '.env')
        Add-Content -Encoding UTF8 $summary '    .env creado desde .env.example'
    }
    $envMap = Read-DotEnv (Join-Path $root '.env')
    $port = if ($envMap['BACKEND_HOST_PORT']) { $envMap['BACKEND_HOST_PORT'] } else { '8080' }
    $base = "http://localhost:$port"

    if (Invoke-Step 'compose-up' 'docker compose up -d --build' $root) {
        $ready = Wait-HttpStatus "$base/actuator/health/readiness" 200 240
        Write-Result 'readiness-200' ($ready.Code -eq 200) "($($ready.Code)) $($ready.Body)"
        $live = Get-HttpStatus "$base/actuator/health/liveness"
        Write-Result 'liveness-200' ($live.Code -eq 200 -and $live.Body -match '"status":"UP"') "($($live.Code)) $($live.Body)"
        $health = Get-HttpStatus "$base/actuator/health"
        Write-Result 'health-sin-detalles' ($health.Code -eq 200 -and $health.Body -notmatch 'components') "($($health.Code)) $($health.Body)"
        $prom = Get-HttpStatus "$base/actuator/prometheus"
        Write-Result 'prometheus-requiere-auth' ($prom.Code -eq 401) "($($prom.Code))"
        $envEp = Get-HttpStatus "$base/actuator/env"
        Write-Result 'env-no-expuesto' ($envEp.Code -eq 404) "($($envEp.Code))"
        $fe = Get-HttpStatus 'http://localhost:3000/'
        Write-Result 'frontend-200' ($fe.Code -eq 200) "($($fe.Code))"

        cmd /c "docker compose stop postgres > nul 2>&1"
        $notReady = Wait-HttpStatus "$base/actuator/health/readiness" 503 60
        Write-Result 'readiness-503-sin-postgres' ($notReady.Code -eq 503) "($($notReady.Code)) $($notReady.Body)"
        $live2 = Get-HttpStatus "$base/actuator/health/liveness"
        Write-Result 'liveness-200-sin-postgres' ($live2.Code -eq 200) "($($live2.Code)) $($live2.Body)"
        cmd /c "docker compose start postgres > nul 2>&1"
        $back = Wait-HttpStatus "$base/actuator/health/readiness" 200 120
        Write-Result 'readiness-recupera-200' ($back.Code -eq 200) "($($back.Code))"

        # Todas las líneas (sin prefijo de servicio) deben ser JSON: una línea de texto plano también es un fallo.
        cmd /c "docker compose logs --no-color --no-log-prefix --tail 300 backend > `"$logDir\backend-container.log`" 2>&1"
        $jsonLines = @(Get-Content "$logDir\backend-container.log" | Where-Object { $_.Trim() })
        $invalid = 0
        foreach ($l in $jsonLines) {
            try { $obj = $l | ConvertFrom-Json; if (-not $obj.'@timestamp') { $invalid++ } } catch { $invalid++ }
        }
        Write-Result 'logs-json-validos' ($jsonLines.Count -gt 0 -and $invalid -eq 0) "($($jsonLines.Count) lineas, $invalid invalidas)"
    }

    # ---------- 4. Observabilidad ----------
    if (-not $SkipObservability) {
        $env:LOGSTASH_ENABLED = 'true'
        if (Invoke-Step 'compose-observability-up' 'docker compose --profile observability up -d --build' $root) {
            $esPort = if ($envMap['ELASTICSEARCH_HOST_PORT']) { $envMap['ELASTICSEARCH_HOST_PORT'] } else { '9200' }
            $pair = 'elastic:' + $envMap['ELASTIC_PASSWORD']
            $auth = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair)) }
            $readyObs = Wait-HttpStatus "$base/actuator/health/readiness" 200 240
            Write-Result 'readiness-200-con-logstash' ($readyObs.Code -eq 200) "($($readyObs.Code))"
            for ($i = 0; $i -lt 10; $i++) { $null = Get-HttpStatus "$base/actuator/health"; Start-Sleep -Milliseconds 300 }
            $count = $null
            $deadline = (Get-Date).AddSeconds(180)
            do {
                Start-Sleep -Seconds 10
                try {
                    $r = Invoke-RestMethod -Uri "http://localhost:$esPort/lebane-logs-*/_count" -Headers $auth -TimeoutSec 10
                    $count = $r.count
                } catch { $count = $null }
            } while ((-not $count) -and (Get-Date) -lt $deadline)
            Write-Result 'logs-en-elasticsearch' ([bool]$count) "(documentos: $count)"
            cmd /c "docker compose --profile observability ps > `"$logDir\compose-ps.log`" 2>&1"

            cmd /c "docker compose stop logstash > nul 2>&1"
            Start-Sleep -Seconds 5
            $sw = [Diagnostics.Stopwatch]::StartNew()
            $r2 = Get-HttpStatus "$base/actuator/health/readiness"
            $sw.Stop()
            Write-Result 'readiness-200-con-logstash-caido' ($r2.Code -eq 200 -and $sw.ElapsedMilliseconds -lt 2000) "($($r2.Code), $($sw.ElapsedMilliseconds) ms)"
            cmd /c "docker compose start logstash > nul 2>&1"
        }
        Remove-Item Env:LOGSTASH_ENABLED -ErrorAction SilentlyContinue
    }
}

Write-Host ''
Write-Host "Resumen en verificacion\resumen.txt" -ForegroundColor Cyan
Get-Content $summary
