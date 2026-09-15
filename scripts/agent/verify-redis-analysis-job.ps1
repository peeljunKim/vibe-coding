# Docker Redis 분석 작업 저장소 통합 검증
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$backendRoot = Join-Path $repoRoot 'backend'
$environmentPath = Join-Path $repoRoot '.env'
$javaPath = 'C:\Program Files\Java\jdk-17\bin\java.exe'
$wrapperJar = Join-Path $backendRoot '.mvn\wrapper\maven-wrapper.jar'
$containerName = "news-verification-redis-test-$([Guid]::NewGuid().ToString('N'))"
$containerStarted = $false

foreach ($requiredPath in @($javaPath, $wrapperJar)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required Redis integration test file missing: $requiredPath"
    }
}

function Read-EnvironmentValues {
    param(
        [Parameter(Mandatory)]
        [string] $Path
    )

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^(?<name>[A-Z0-9_]+)=(?<value>.*)$') {
            $values[$Matches.name] = $Matches.value
        }
    }
    return $values
}

$localValues = @{}
if (Test-Path -LiteralPath $environmentPath -PathType Leaf) {
    $localValues = Read-EnvironmentValues -Path $environmentPath
}
$redisPassword = [Environment]::GetEnvironmentVariable('REDIS_PASSWORD', 'Process')
if (-not $redisPassword) {
    $redisPassword = $localValues['REDIS_PASSWORD']
}
$secureRedisPassword = $null
$redisPasswordPointer = [IntPtr]::Zero
if (-not $redisPassword -or $redisPassword -match '^replace-with-') {
    $redisPassword = $null
    $secureRedisPassword = Read-Host 'Redis Test Password' -AsSecureString
}

$redisTestPort = $localValues['REDIS_TEST_PORT']
if (-not $redisTestPort) {
    $redisTestPort = '6380'
}
if ($redisTestPort -notmatch '^\d+$') {
    throw 'REDIS_TEST_PORT must be a number'
}

$environmentNames = @(
    'REDIS_PASSWORD'
    'REDIS_IT_ENABLED'
    'REDIS_TEST_HOST'
    'REDIS_TEST_PORT'
    'REDIS_TEST_PASSWORD'
    'REDIS_TEST_NAMESPACE'
)
$environmentBackup = @{}
foreach ($name in $environmentNames) {
    $environmentBackup[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    if ($secureRedisPassword) {
        $redisPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureRedisPassword)
        $redisPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($redisPasswordPointer)
    }
    if (-not $redisPassword) {
        throw 'Redis test password is required'
    }

    $env:REDIS_PASSWORD = $redisPassword
    $env:REDIS_IT_ENABLED = 'true'
    $env:REDIS_TEST_HOST = '127.0.0.1'
    $env:REDIS_TEST_PORT = $redisTestPort
    $env:REDIS_TEST_PASSWORD = $redisPassword
    $env:REDIS_TEST_NAMESPACE = "news-verification:test:analysis-job:$([Guid]::NewGuid().ToString('N'))"

    $containerId = & docker run --detach --rm --name $containerName `
        --publish "127.0.0.1:${redisTestPort}:6379" `
        --env REDIS_PASSWORD `
        --health-cmd 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning ping' `
        --health-interval 2s `
        --health-timeout 2s `
        --health-retries 20 `
        redis:8.8-alpine `
        sh -c 'exec redis-server --bind 0.0.0.0 --protected-mode yes --port 6379 --save "" --appendonly no --maxmemory 64mb --maxmemory-policy noeviction --requirepass "$REDIS_PASSWORD"'
    if ($LASTEXITCODE -ne 0 -or -not $containerId) {
        throw "Redis test container startup failed with exit code $LASTEXITCODE"
    }
    $containerStarted = $true

    $healthDeadline = (Get-Date).AddSeconds(60)
    do {
        $containerHealth = & docker inspect --format '{{.State.Health.Status}}' $containerName
        if ($LASTEXITCODE -ne 0) {
            throw 'Redis test container health inspection failed'
        }
        if ($containerHealth -eq 'healthy') {
            break
        }
        if ($containerHealth -eq 'unhealthy') {
            throw 'Redis test container became unhealthy'
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $healthDeadline)

    if ($containerHealth -ne 'healthy') {
        throw 'Redis test container health check timed out'
    }

    $redisVersion = & docker exec $containerName redis-server --version
    if ($LASTEXITCODE -ne 0 -or $redisVersion -notmatch 'v=8\.8\.') {
        throw 'Redis test container version 8.8 verification failed'
    }
    Write-Host '[PASS] Docker Redis test version: 8.8'

    Push-Location $backendRoot
    try {
        & $javaPath "-Dmaven.multiModuleProjectDirectory=$backendRoot" '-classpath' $wrapperJar `
            'org.apache.maven.wrapper.MavenWrapperMain' '-q' '-Dtest=RedisAnalysisJobStoreIT' 'test'
        if ($LASTEXITCODE -ne 0) {
            throw "Redis analysis job integration test failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }

    Write-Host '[PASS] Redis AnalysisJobStore integration test'
}
finally {
    if ($containerStarted) {
        & docker stop $containerName | Out-Null
    }
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $environmentBackup[$name], 'Process')
    }
    if ($redisPasswordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($redisPasswordPointer)
    }
    $redisPassword = $null
    if ($localValues) {
        $localValues.Clear()
    }
}
