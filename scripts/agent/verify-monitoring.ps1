# Local Actuator·Prometheus·Grafana 통합 검증
[CmdletBinding()]
param(
    [switch] $ConfigurationOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$backendRoot = Join-Path $repoRoot 'backend'
$environmentPath = Join-Path $repoRoot '.env'
$composePath = Join-Path $repoRoot 'docker-compose.yml'
$utilitiesPath = Join-Path $PSScriptRoot 'script-utilities.ps1'
$dashboardPath = Join-Path $repoRoot 'infra\grafana\dashboards\news-verification-overview.json'
$requiredPaths = @(
    $composePath,
    $utilitiesPath,
    $dashboardPath,
    (Join-Path $repoRoot 'infra\prometheus\prometheus.yml'),
    (Join-Path $repoRoot 'infra\grafana\provisioning\dashboards\dashboards.yml'),
    (Join-Path $repoRoot 'infra\grafana\provisioning\datasources\prometheus.yml')
)

foreach ($path in $requiredPaths) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required monitoring file missing: $path"
    }
}

. $utilitiesPath

$dashboard = Get-Content -Raw -LiteralPath $dashboardPath | ConvertFrom-Json
if ($dashboard.uid -ne 'news-verification-overview' -or $dashboard.title -ne '기사체크 서비스 개요') {
    throw 'Grafana overview dashboard identity is invalid'
}
if ($dashboard.panels.Count -ne 6) {
    throw "Grafana overview dashboard must contain 6 panels, found $($dashboard.panels.Count)"
}
foreach ($panel in $dashboard.panels) {
    if (-not $panel.targets -or -not ($panel.targets | Where-Object { -not [string]::IsNullOrWhiteSpace($_.expr) })) {
        throw "Grafana panel query missing: $($panel.title)"
    }
}
Write-Host '[PASS] Monitoring configuration files and dashboard contract'

$previousComposeValues = @{}
foreach ($name in @('REDIS_PASSWORD', 'GRAFANA_ADMIN_PASSWORD')) {
    $previousComposeValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    if (-not $previousComposeValues[$name]) {
        [Environment]::SetEnvironmentVariable($name, 'monitoring-configuration-check', 'Process')
    }
}
try {
    & docker compose --file $composePath config --quiet
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose monitoring configuration failed with exit code $LASTEXITCODE"
    }
}
finally {
    foreach ($name in $previousComposeValues.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousComposeValues[$name], 'Process')
    }
}
Write-Host '[PASS] Docker Compose monitoring configuration'

if ($ConfigurationOnly) {
    Write-Host '[PASS] Monitoring configuration-only verification'
    exit 0
}

if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    throw 'Ignored Local .env is required for monitoring integration verification'
}

function Get-ContainerSnapshot {
    param([Parameter(Mandatory)][string] $Name)

    $running = & docker inspect --format '{{.State.Running}}' $Name 2>$null
    if ($LASTEXITCODE -ne 0) {
        return [pscustomobject]@{ Exists = $false; Running = $false }
    }
    return [pscustomobject]@{
        Exists = $true
        Running = ($running.Trim() -eq 'true')
    }
}

function Wait-HttpOk {
    param(
        [Parameter(Mandatory)][string] $Uri,
        [int] $TimeoutSeconds = 60,
        [hashtable] $Headers = @{}
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -Uri $Uri -Headers $Headers -UseBasicParsing `
                -SkipHttpErrorCheck -TimeoutSec 3
            if ($response.StatusCode -eq 200) {
                return $response
            }
        }
        catch {
            # 준비 대기
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)

    throw "Monitoring endpoint readiness timed out: $Uri"
}

function Invoke-PrometheusQuery {
    param([Parameter(Mandatory)][string] $Query)

    $encodedQuery = [Uri]::EscapeDataString($Query)
    $response = Invoke-RestMethod -Uri "http://127.0.0.1:9090/api/v1/query?query=$encodedQuery" `
        -TimeoutSec 5
    if ($response.status -ne 'success') {
        throw "Prometheus query failed: $Query"
    }
    return $response.data.result
}

$localValues = Read-EnvironmentValues -Path $environmentPath
$grafanaUser = Get-ConfiguredValue -Values $localValues -Name 'GRAFANA_ADMIN_USER' -Fallback 'admin'
$redisPassword = [Environment]::GetEnvironmentVariable('REDIS_PASSWORD', 'Process')
if (-not $redisPassword) {
    $redisPassword = Get-ConfiguredValue -Values $localValues -Name 'REDIS_PASSWORD'
}
$grafanaPassword = [Environment]::GetEnvironmentVariable('GRAFANA_ADMIN_PASSWORD', 'Process')
if (-not $grafanaPassword) {
    $grafanaPassword = Get-ConfiguredValue -Values $localValues -Name 'GRAFANA_ADMIN_PASSWORD'
}
if (-not $redisPassword -or -not $grafanaPassword) {
    throw 'REDIS_PASSWORD and GRAFANA_ADMIN_PASSWORD must be configured in Process environment or ignored .env'
}
$runtimeEnvironmentBackup = @{}
foreach ($name in @('REDIS_PASSWORD', 'GRAFANA_ADMIN_PASSWORD')) {
    $runtimeEnvironmentBackup[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
[Environment]::SetEnvironmentVariable('REDIS_PASSWORD', $redisPassword, 'Process')
[Environment]::SetEnvironmentVariable('GRAFANA_ADMIN_PASSWORD', $grafanaPassword, 'Process')

$containerNames = [ordered]@{
    redis = 'news-verification-redis'
    prometheus = 'news-verification-prometheus'
    grafana = 'news-verification-grafana'
}
$snapshots = @{}
$startedServices = [Collections.Generic.List[string]]::new()
$createdServices = [Collections.Generic.List[string]]::new()
$backendProcess = $null
$backendStarted = $false
$temporaryDirectory = Join-Path ([IO.Path]::GetTempPath()) "news-verification-monitoring-$([guid]::NewGuid().ToString('N'))"
$previousMailHealth = [Environment]::GetEnvironmentVariable('MANAGEMENT_HEALTH_MAIL_ENABLED', 'Process')
$previousAnalysisProvider = [Environment]::GetEnvironmentVariable('HEALTH_ANALYSIS_PROVIDER', 'Process')

try {
    foreach ($service in $containerNames.Keys) {
        $snapshot = Get-ContainerSnapshot -Name $containerNames[$service]
        $snapshots[$service] = $snapshot
        if (-not $snapshot.Running) {
            $startedServices.Add($service)
        }
        if (-not $snapshot.Exists) {
            $createdServices.Add($service)
        }
    }

    if ($startedServices.Count -gt 0) {
        Push-Location $repoRoot
        try {
            & docker compose --file $composePath up --detach @($startedServices)
            if ($LASTEXITCODE -ne 0) {
                throw "Monitoring container startup failed with exit code $LASTEXITCODE"
            }
        }
        finally {
            Pop-Location
        }
    }
    Write-Host '[PASS] Redis, Prometheus and Grafana containers ready'

    $backendResponse = $null
    try {
        $backendResponse = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/actuator/health' `
            -UseBasicParsing -SkipHttpErrorCheck -TimeoutSec 2
    }
    catch {
        $backendResponse = $null
    }

    if (-not $backendResponse -or $backendResponse.StatusCode -ne 200) {
        $javaPath = Resolve-Java17Path
        $wrapperJar = Join-Path $backendRoot '.mvn\wrapper\maven-wrapper.jar'
        if (-not (Test-Path -LiteralPath $wrapperJar -PathType Leaf)) {
            throw 'Maven Wrapper JAR is required to start the monitoring Backend'
        }

        Push-Location $backendRoot
        try {
            & $javaPath "-Dmaven.multiModuleProjectDirectory=$backendRoot" '-classpath' $wrapperJar `
                'org.apache.maven.wrapper.MavenWrapperMain' '-q' '-DskipTests' 'package'
            if ($LASTEXITCODE -ne 0) {
                throw "Backend monitoring package failed with exit code $LASTEXITCODE"
            }
        }
        finally {
            Pop-Location
        }

        $backendJar = Join-Path $backendRoot 'target\news-verification-backend-0.1.0-SNAPSHOT.jar'
        if (-not (Test-Path -LiteralPath $backendJar -PathType Leaf)) {
            throw 'Backend executable JAR was not created'
        }

        [void](New-Item -ItemType Directory -Path $temporaryDirectory -Force)
        [Environment]::SetEnvironmentVariable('MANAGEMENT_HEALTH_MAIL_ENABLED', 'false', 'Process')
        [Environment]::SetEnvironmentVariable('HEALTH_ANALYSIS_PROVIDER', 'mock', 'Process')
        $backendProcess = Start-Process -FilePath $javaPath `
            -ArgumentList @('-jar', $backendJar) `
            -WorkingDirectory $backendRoot `
            -WindowStyle Hidden `
            -PassThru `
            -RedirectStandardOutput (Join-Path $temporaryDirectory 'backend.out.log') `
            -RedirectStandardError (Join-Path $temporaryDirectory 'backend.err.log')
        $backendStarted = $true
        [void](Wait-HttpOk -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSeconds 90)
    }
    Write-Host '[PASS] Backend Actuator health'

    $metricsResponse = Wait-HttpOk -Uri 'http://127.0.0.1:8080/actuator/prometheus' -TimeoutSeconds 30
    foreach ($metricName in @('jvm_memory_used_bytes', 'http_server_requests_seconds_count')) {
        if (-not $metricsResponse.Content.Contains($metricName)) {
            throw "Actuator Prometheus metric missing: $metricName"
        }
    }
    Write-Host '[PASS] Backend Actuator Prometheus metrics'

    [void](Wait-HttpOk -Uri 'http://127.0.0.1:9090/-/ready' -TimeoutSeconds 60)
    $targetDeadline = (Get-Date).AddSeconds(45)
    do {
        $upResult = Invoke-PrometheusQuery -Query 'up{job="news-verification-api"}'
        if ($upResult.Count -gt 0 -and $upResult[0].value[1] -eq '1') {
            break
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $targetDeadline)
    if ($upResult.Count -eq 0 -or $upResult[0].value[1] -ne '1') {
        throw 'Prometheus Backend target did not become UP'
    }

    foreach ($query in @(
            'jvm_memory_used_bytes{application="news-verification-api"}',
            'http_server_requests_seconds_count{application="news-verification-api"}'
        )) {
        $result = Invoke-PrometheusQuery -Query $query
        if ($result.Count -eq 0) {
            throw "Prometheus expected series missing: $query"
        }
    }
    Write-Host '[PASS] Prometheus Backend target and metric queries'

    [void](Wait-HttpOk -Uri 'http://127.0.0.1:3000/api/health' -TimeoutSeconds 60)
    $basicValue = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("$grafanaUser`:$grafanaPassword"))
    $grafanaHeaders = @{ Authorization = "Basic $basicValue" }
    $datasource = Invoke-RestMethod -Uri 'http://127.0.0.1:3000/api/datasources/uid/prometheus' `
        -Headers $grafanaHeaders -TimeoutSec 5
    if ($datasource.type -ne 'prometheus' -or $datasource.url -ne 'http://prometheus:9090') {
        throw 'Grafana Prometheus data source is invalid'
    }
    $registeredDashboard = Invoke-RestMethod `
        -Uri 'http://127.0.0.1:3000/api/dashboards/uid/news-verification-overview' `
        -Headers $grafanaHeaders -TimeoutSec 5
    if ($registeredDashboard.dashboard.title -ne '기사체크 서비스 개요') {
        throw 'Grafana overview dashboard was not provisioned'
    }

    foreach ($panel in $dashboard.panels) {
        foreach ($target in $panel.targets) {
            if (-not [string]::IsNullOrWhiteSpace($target.expr)) {
                [void](Invoke-PrometheusQuery -Query $target.expr)
            }
        }
    }
    Write-Host '[PASS] Grafana data source, dashboard and panel queries'
    Write-Host '[PASS] Local Actuator, Prometheus and Grafana monitoring integration'
}
finally {
    if ($backendStarted -and $backendProcess -and -not $backendProcess.HasExited) {
        Stop-Process -Id $backendProcess.Id
        [void]$backendProcess.WaitForExit(10000)
    }

    if ($startedServices.Count -gt 0) {
        Push-Location $repoRoot
        try {
            & docker compose --file $composePath stop @($startedServices) | Out-Null
            if ($createdServices.Count -gt 0) {
                & docker compose --file $composePath rm --force @($createdServices) | Out-Null
            }
        }
        finally {
            Pop-Location
        }
    }

    $resolvedTemporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $resolvedTemporaryDirectory = [IO.Path]::GetFullPath($temporaryDirectory)
    if ($resolvedTemporaryDirectory.StartsWith($resolvedTemporaryRoot, [StringComparison]::OrdinalIgnoreCase) `
            -and (Test-Path -LiteralPath $resolvedTemporaryDirectory -PathType Container)) {
        Remove-Item -LiteralPath $resolvedTemporaryDirectory -Recurse -Force
    }

    [Environment]::SetEnvironmentVariable('MANAGEMENT_HEALTH_MAIL_ENABLED', $previousMailHealth, 'Process')
    [Environment]::SetEnvironmentVariable('HEALTH_ANALYSIS_PROVIDER', $previousAnalysisProvider, 'Process')
    foreach ($name in $runtimeEnvironmentBackup.Keys) {
        [Environment]::SetEnvironmentVariable($name, $runtimeEnvironmentBackup[$name], 'Process')
    }
    $redisPassword = $null
    $grafanaPassword = $null
    $basicValue = $null
    if ($localValues) {
        $localValues.Clear()
    }
}
