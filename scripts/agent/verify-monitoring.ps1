# Local Actuator·Prometheus·Alertmanager·Grafana 통합 검증
[CmdletBinding()]
param(
    [switch] $ConfigurationOnly,
    [switch] $SendTestAlert
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$backendRoot = Join-Path $repoRoot 'backend'
$environmentPath = Join-Path $repoRoot '.env'
$composePath = Join-Path $repoRoot 'docker-compose.yml'
$utilitiesPath = Join-Path $PSScriptRoot 'script-utilities.ps1'
$dashboardPath = Join-Path $repoRoot 'infra\grafana\dashboards\news-verification-overview.json'
$alertRulesPath = Join-Path $repoRoot 'infra\prometheus\rules\news-verification-alerts.yml'
$alertRulesTestPath = Join-Path $repoRoot 'infra\prometheus\rules\news-verification-alerts.test.yml'
$alertmanagerPath = Join-Path $repoRoot 'infra\alertmanager\alertmanager.yml'
$requiredPaths = @(
    $composePath,
    $utilitiesPath,
    $dashboardPath,
    $alertRulesPath,
    $alertRulesTestPath,
    $alertmanagerPath,
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
if ($dashboard.panels.Count -ne 11) {
    throw "Grafana overview dashboard must contain 11 panels, found $($dashboard.panels.Count)"
}
foreach ($panel in $dashboard.panels) {
    if (-not $panel.targets -or -not ($panel.targets | Where-Object { -not [string]::IsNullOrWhiteSpace($_.expr) })) {
        throw "Grafana panel query missing: $($panel.title)"
    }
}
Write-Host '[PASS] Monitoring configuration files and dashboard contract'

$alertRules = Get-Content -Raw -LiteralPath $alertRulesPath
foreach ($alertName in @(
        'NewsVerificationBackendUnavailable',
        'NewsVerificationAnalysisRedisUnavailable',
        'NewsVerificationAnalysisQueueSaturated',
        'NewsVerificationWorkerFailureRateHigh',
        'NewsVerificationWorkerLatencyHigh',
        'NewsVerificationArticleExtractionFailureRateHigh'
    )) {
    if (-not $alertRules.Contains("alert: $alertName")) {
        throw "Required Prometheus alert rule missing: $alertName"
    }
}
if (-not $alertRules.Contains('>= 5') -or -not $alertRules.Contains('>= 10')) {
    throw 'Ratio alert rules must include minimum sample boundaries'
}

$alertmanagerConfiguration = Get-Content -Raw -LiteralPath $alertmanagerPath
if (-not $alertmanagerConfiguration.Contains('smtp_auth_password_file: /run/secrets/alertmanager-smtp-password')) {
    throw 'Alertmanager SMTP password must use a Docker Secret file'
}
if (-not $alertmanagerConfiguration.Contains('to: reportcheck104@gmail.com')) {
    throw 'Alertmanager default email recipient is invalid'
}
if (-not $alertmanagerConfiguration.Contains('api_url_file: /run/secrets/alertmanager-slack-webhook') `
        -or -not $alertmanagerConfiguration.Contains('channel: "#monitoring-alerts"')) {
    throw 'Alertmanager Slack receiver must use the Docker Secret and monitoring channel'
}
if ($alertmanagerConfiguration -match '(?m)^\s*smtp_auth_password:\s*\S+') {
    throw 'Alertmanager SMTP password must not be stored in tracked configuration'
}
if ($alertmanagerConfiguration -match '(?m)^\s*api_url:\s*\S+') {
    throw 'Alertmanager Slack Webhook URL must not be stored in tracked configuration'
}
Write-Host '[PASS] Prometheus alert rules and Alertmanager contract'

$previousComposeValues = @{}
foreach ($name in @('REDIS_PASSWORD', 'GRAFANA_ADMIN_PASSWORD', 'MAIL_APP_PASSWORD', 'SLACK_WEBHOOK_URL')) {
    $previousComposeValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    if (-not $previousComposeValues[$name]) {
        [Environment]::SetEnvironmentVariable($name, 'monitoring-configuration-check', 'Process')
    }
}
try {
    & docker compose --file $composePath --profile alerts config --quiet
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose monitoring configuration failed with exit code $LASTEXITCODE"
    }

    & docker compose --file $composePath run --rm --no-deps --entrypoint promtool prometheus `
        test rules /etc/prometheus/rules/news-verification-alerts.test.yml
    if ($LASTEXITCODE -ne 0) {
        throw "Prometheus alert rule fixture verification failed with exit code $LASTEXITCODE"
    }

    & docker compose --file $composePath --profile alerts run --rm --no-deps `
        --entrypoint amtool alertmanager check-config /etc/alertmanager/alertmanager.yml
    if ($LASTEXITCODE -ne 0) {
        throw "Alertmanager configuration verification failed with exit code $LASTEXITCODE"
    }
}
finally {
    foreach ($name in $previousComposeValues.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousComposeValues[$name], 'Process')
    }
}
Write-Host '[PASS] Docker Compose, Prometheus fixture and Alertmanager configuration'

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
$mailAppPassword = [Environment]::GetEnvironmentVariable('MAIL_APP_PASSWORD', 'Process')
if (-not $mailAppPassword) {
    $mailAppPassword = Get-ConfiguredValue -Values $localValues -Name 'MAIL_APP_PASSWORD'
}
if (-not $mailAppPassword) {
    throw 'MAIL_APP_PASSWORD must be configured in Process environment or ignored .env'
}
$slackWebhookUrl = [Environment]::GetEnvironmentVariable('SLACK_WEBHOOK_URL', 'Process')
if (-not $slackWebhookUrl) {
    $slackWebhookUrl = Get-ConfiguredValue -Values $localValues -Name 'SLACK_WEBHOOK_URL'
}
if (-not $slackWebhookUrl) {
    throw 'SLACK_WEBHOOK_URL must be configured in Process environment or ignored .env'
}
$runtimeEnvironmentBackup = @{}
foreach ($name in @('REDIS_PASSWORD', 'GRAFANA_ADMIN_PASSWORD', 'MAIL_APP_PASSWORD', 'SLACK_WEBHOOK_URL')) {
    $runtimeEnvironmentBackup[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
[Environment]::SetEnvironmentVariable('REDIS_PASSWORD', $redisPassword, 'Process')
[Environment]::SetEnvironmentVariable('GRAFANA_ADMIN_PASSWORD', $grafanaPassword, 'Process')
[Environment]::SetEnvironmentVariable('MAIL_APP_PASSWORD', $mailAppPassword, 'Process')
[Environment]::SetEnvironmentVariable('SLACK_WEBHOOK_URL', $slackWebhookUrl, 'Process')

$containerNames = [ordered]@{
    redis = 'news-verification-redis'
    prometheus = 'news-verification-prometheus'
    grafana = 'news-verification-grafana'
    alertmanager = 'news-verification-alertmanager'
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
            & docker compose --file $composePath --profile alerts up --detach @($startedServices)
            if ($LASTEXITCODE -ne 0) {
                throw "Monitoring container startup failed with exit code $LASTEXITCODE"
            }
        }
        finally {
            Pop-Location
        }
    }
    Write-Host '[PASS] Redis, Prometheus, Grafana and Alertmanager containers ready'

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
    foreach ($metricName in @(
            'jvm_memory_used_bytes',
            'http_server_requests_seconds_count',
            'news_verification_analysis_requests_total',
            'news_verification_analysis_worker_results_total',
            'news_verification_analysis_worker_duration_seconds',
            'news_verification_analysis_cache_lookups_total',
            'news_verification_article_extractions_total'
        )) {
        if (-not $metricsResponse.Content.Contains($metricName)) {
            throw "Actuator Prometheus metric missing: $metricName"
        }
    }
    Write-Host '[PASS] Backend Actuator Prometheus metrics'

    [void](Wait-HttpOk -Uri 'http://127.0.0.1:9090/-/ready' -TimeoutSeconds 60)
    [void](Wait-HttpOk -Uri 'http://127.0.0.1:9093/-/ready' -TimeoutSeconds 60)

    $rulesResponse = Invoke-RestMethod -Uri 'http://127.0.0.1:9090/api/v1/rules?type=alert' -TimeoutSec 5
    if ($rulesResponse.status -ne 'success') {
        throw 'Prometheus alert rules API failed'
    }
    $loadedAlerts = @($rulesResponse.data.groups.rules | ForEach-Object { $_.name })
    foreach ($alertName in @(
            'NewsVerificationBackendUnavailable',
            'NewsVerificationAnalysisRedisUnavailable',
            'NewsVerificationAnalysisQueueSaturated',
            'NewsVerificationWorkerFailureRateHigh',
            'NewsVerificationWorkerLatencyHigh',
            'NewsVerificationArticleExtractionFailureRateHigh'
        )) {
        if ($loadedAlerts -notcontains $alertName) {
            throw "Prometheus alert rule was not loaded: $alertName"
        }
    }
    Write-Host '[PASS] Prometheus alert rules loaded'

    $alertmanagerStatus = Invoke-RestMethod -Uri 'http://127.0.0.1:9093/api/v2/status' -TimeoutSec 5
    if (-not $alertmanagerStatus.config.original.Contains('reportcheck104@gmail.com') `
            -or -not $alertmanagerStatus.config.original.Contains('#monitoring-alerts')) {
        throw 'Alertmanager notification receiver was not loaded'
    }
    Write-Host '[PASS] Alertmanager email and Slack receivers loaded'

    if ($SendTestAlert) {
        $testAlert = ConvertTo-Json -InputObject @(
            @{
                labels = @{
                    alertname = 'NewsVerificationAlertDeliveryTest'
                    severity = 'info'
                    service = 'monitoring'
                }
                annotations = @{
                    summary = '기사체크 Local 경보 전달 시험'
                    description = '사용자가 요청한 Gmail SMTP와 Slack 경보 전달 확인입니다.'
                }
                startsAt = [DateTimeOffset]::UtcNow.ToString('o')
                endsAt = [DateTimeOffset]::UtcNow.AddMinutes(2).ToString('o')
            }
        ) -Depth 5
        Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:9093/api/v2/alerts' `
            -ContentType 'application/json' -Body $testAlert -TimeoutSec 5 | Out-Null
        Write-Host '[PASS] Alertmanager test alert accepted'

        Start-Sleep -Seconds 35
        $alertmanagerLogs = & docker logs --since 1m $containerNames.alertmanager 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw 'Alertmanager delivery log inspection failed'
        }
        if (($alertmanagerLogs -join "`n") -match 'level=error|Notify for alerts failed|notify retry canceled') {
            throw 'Alertmanager reported a notification delivery error'
        }
        Write-Host '[PASS] Alertmanager Gmail and Slack notification attempts completed without reported error'
    }
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
            'http_server_requests_seconds_count{application="news-verification-api"}',
            'news_verification_analysis_requests_total{application="news-verification-api"}',
            'news_verification_analysis_worker_results_total{application="news-verification-api"}',
            'news_verification_analysis_worker_duration_seconds_count{application="news-verification-api"}',
            'news_verification_analysis_cache_lookups_total{application="news-verification-api"}',
            'news_verification_article_extractions_total{application="news-verification-api"}'
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
    $mailAppPassword = $null
    $slackWebhookUrl = $null
    $basicValue = $null
    if ($localValues) {
        $localValues.Clear()
    }
}
