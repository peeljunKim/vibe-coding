# Local Frontend·Backend·MySQL·Redis Full-stack Smoke 검증
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

# Java 17 실행 파일 탐색
function Resolve-Java17Path {
    $candidates = [Collections.Generic.List[string]]::new()
    if ($env:JAVA_HOME) {
        $candidates.Add((Join-Path $env:JAVA_HOME 'bin\java.exe'))
    }

    foreach ($pathJava in Get-Command 'java.exe' -All -ErrorAction SilentlyContinue) {
        $candidates.Add($pathJava.Source)
    }

    $candidates.Add('C:\Program Files\Java\jdk-17\bin\java.exe')
    $candidates.Add('C:\Users\82109\scoop\apps\openjdk17\current\bin\java.exe')

    foreach ($candidate in $candidates | Select-Object -Unique) {
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            continue
        }
        $versionOutput = & $candidate -version 2>&1 | Out-String
        if ($LASTEXITCODE -eq 0 -and $versionOutput -match 'version "17(?:[.\-"]|$)') {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw 'Java 17 executable not found in JAVA_HOME, PATH, or known Local paths'
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$backendRoot = Join-Path $repoRoot 'backend'
$frontendRoot = Join-Path $repoRoot 'frontend'
$environmentPath = Join-Path $repoRoot '.env'
$validationPath = Join-Path $PSScriptRoot 'native-mysql-validation.ps1'
$javaPath = Resolve-Java17Path
$mysqlPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$wrapperJar = Join-Path $backendRoot '.mvn\wrapper\maven-wrapper.jar'
$backendJar = Join-Path $backendRoot 'target\news-verification-backend-0.1.0-SNAPSHOT.jar'
$runId = [Guid]::NewGuid().ToString('N').Substring(0, 8)
$containerName = "news-verification-full-stack-e2e-$runId"
$redisPort = '6382'
$backendPort = '8080'
$publisherName = "E2E Test Publisher $runId"
$publisherHost = "$runId.e2e.news.invalid"
$articleUrl = "https://$publisherHost/article/health"
$articleTitle = '매일 걷기는 건강에 도움을 줍니다'
$username = "smoke1$runId"
$email = "$username@example.com"
$phoneSuffix = ([Math]::Abs([DateTime]::UtcNow.Ticks % 100000000)).ToString('D8')
$phoneNumber = "010-$($phoneSuffix.Substring(0, 4))-$($phoneSuffix.Substring(4, 4))"
$password = "Smoke-$runId-Aa1!"
$inviteCode = 'E2E-INVITE-2026'
$verificationCode = '482916'
$backendProcess = $null
$containerStarted = $false
$publisherId = $null
$temporaryDirectory = Join-Path ([IO.Path]::GetTempPath()) "news-verification-full-stack-e2e-$runId"

foreach ($requiredPath in @(
        $environmentPath,
        $validationPath,
        $javaPath,
        $mysqlPath,
        $wrapperJar,
        (Join-Path $frontendRoot 'playwright.full-stack.config.ts')
    )) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required Full-stack Smoke file missing: $requiredPath"
    }
}

. $validationPath

function Read-EnvironmentValues {
    param([Parameter(Mandatory)][string] $Path)

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^(?<name>[A-Z0-9_]+)=(?<value>.*)$') {
            $values[$Matches.name] = $Matches.value
        }
    }
    return $values
}

function Get-ConfiguredValue {
    param(
        [Parameter(Mandatory)][hashtable] $Values,
        [Parameter(Mandatory)][string] $Name,
        [string] $Fallback = ''
    )

    $value = $Values[$Name]
    if ($value -and $value -notmatch '^replace-with-') {
        return $value
    }
    return $Fallback
}

function Invoke-MySql {
    param(
        [Parameter(Mandatory)][string] $Password,
        [Parameter(Mandatory)][string] $Sql,
        [Parameter(Mandatory)][string] $User,
        [Parameter(Mandatory)][string] $Database
    )

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $mysqlPath
    $startInfo.ArgumentList.Add('--host=127.0.0.1')
    $startInfo.ArgumentList.Add('--port=3306')
    $startInfo.ArgumentList.Add("--user=$User")
    $startInfo.ArgumentList.Add("--database=$Database")
    $startInfo.ArgumentList.Add('--default-character-set=utf8mb4')
    $startInfo.ArgumentList.Add('--batch')
    $startInfo.ArgumentList.Add('--raw')
    $startInfo.ArgumentList.Add('--skip-column-names')
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    $startInfo.Environment['MYSQL_PWD'] = $Password

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void]$process.Start()
    $process.StandardInput.Write($Sql)
    $process.StandardInput.Close()
    $standardOutput = $process.StandardOutput.ReadToEnd().Trim()
    $standardError = $process.StandardError.ReadToEnd().Trim()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        $errorCode = if ($standardError -match 'ERROR\s+(?<code>\d+)') {
            $Matches.code
        }
        else {
            'unknown'
        }
        throw "MySQL Smoke fixture command failed with MySQL error $errorCode"
    }
    return $standardOutput
}

function Wait-BackendReady {
    param([Parameter(Mandatory)][Diagnostics.Process] $Process)

    $deadline = (Get-Date).AddSeconds(60)
    $lastStatusCode = $null
    while ((Get-Date) -lt $deadline) {
        if ($Process.HasExited) {
            throw "Backend stopped before readiness with exit code $($Process.ExitCode)"
        }
        try {
            $response = Invoke-WebRequest -Uri "http://127.0.0.1:$backendPort/actuator/health" `
                -UseBasicParsing -SkipHttpErrorCheck -TimeoutSec 2
            $lastStatusCode = $response.StatusCode
            if ($response.StatusCode -eq 200) {
                return
            }
        }
        catch {
            Start-Sleep -Milliseconds 500
        }
    }
    if ($lastStatusCode) {
        throw "Backend readiness check timed out with HTTP $lastStatusCode"
    }
    throw 'Backend readiness check timed out without an HTTP response'
}

$localValues = Read-EnvironmentValues -Path $environmentPath
$developmentDatabase = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_DATABASE'
$developmentUser = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_USER'
if (-not $developmentDatabase -or -not $developmentUser) {
    throw 'MYSQL_DATABASE and MYSQL_USER must be configured in ignored .env'
}

$testDatabase = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_TEST_DATABASE' -Fallback "${developmentDatabase}_test"
$testUser = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_TEST_USER' -Fallback "${developmentUser}_test"
$testDatabaseUrl = Get-ConfiguredValue -Values $localValues -Name 'TEST_DB_URL' `
    -Fallback "jdbc:mysql://127.0.0.1:3306/$testDatabase`?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"
$testDatabaseUsername = Get-ConfiguredValue -Values $localValues -Name 'TEST_DB_USERNAME' -Fallback $testUser
$testPassword = [Environment]::GetEnvironmentVariable('TEST_DB_PASSWORD', 'Process')
if (-not $testPassword) {
    $testPassword = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_TEST_PASSWORD'
}
if (-not $testPassword) {
    $applicationPassword = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_PASSWORD'
    if (-not $applicationPassword) {
        $applicationPassword = Get-ConfiguredValue -Values $localValues -Name 'DB_PASSWORD'
    }
    if ($applicationPassword) {
        try {
            [void](Invoke-MySql `
                    -Password $applicationPassword `
                    -User $testDatabaseUsername `
                    -Database $testDatabase `
                    -Sql 'SELECT 1;')
            $testPassword = $applicationPassword
        }
        catch {
            $applicationPassword = $null
        }
    }
}
$secureTestPassword = $null
$testPasswordPointer = [IntPtr]::Zero
if (-not $testPassword) {
    $secureTestPassword = Read-Host "$testUser Password" -AsSecureString
}

$redisPassword = [Environment]::GetEnvironmentVariable('REDIS_PASSWORD', 'Process')
if (-not $redisPassword) {
    $redisPassword = Get-ConfiguredValue -Values $localValues -Name 'REDIS_PASSWORD'
}
$secureRedisPassword = $null
$redisPasswordPointer = [IntPtr]::Zero
if (-not $redisPassword) {
    $secureRedisPassword = Read-Host 'Redis Test Password' -AsSecureString
}

Assert-NativeMySqlTestConnection `
    -DatabaseUrl $testDatabaseUrl `
    -Username $testDatabaseUsername `
    -ExpectedDatabase $testDatabase `
    -ExpectedUsername $testUser `
    -DevelopmentDatabase $developmentDatabase `
    -DevelopmentUsername $developmentUser | Out-Null

$environmentNames = @(
    'SPRING_PROFILES_ACTIVE', 'DB_URL', 'DB_USERNAME', 'DB_PASSWORD',
    'SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD',
    'REDIS_HOST', 'REDIS_PORT', 'REDIS_USERNAME', 'REDIS_PASSWORD', 'REDIS_SSL_ENABLED',
    'REDIS_KEY_PREFIX', 'REDIS_SESSION_NAMESPACE', 'HEALTH_ANALYSIS_PROVIDER',
    'LOOKUP_HMAC_KEY', 'INVITE_CODE_1', 'E2E_SIGNUP_CODE', 'SERVER_PORT',
    'MANAGEMENT_HEALTH_MAIL_ENABLED',
    'APP_BASE_URL', 'SESSION_COOKIE_SECURE', 'E2E_USERNAME', 'E2E_EMAIL',
    'E2E_PHONE_NUMBER', 'E2E_PASSWORD', 'E2E_INVITE_CODE',
    'E2E_PUBLISHER_NAME', 'E2E_ARTICLE_URL', 'E2E_ARTICLE_TITLE'
)
$environmentBackup = @{}
foreach ($name in $environmentNames) {
    $environmentBackup[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    if ($secureTestPassword) {
        $testPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureTestPassword)
        $testPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($testPasswordPointer)
    }
    if ($secureRedisPassword) {
        $redisPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureRedisPassword)
        $redisPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($redisPasswordPointer)
    }
    if (-not $testPassword -or -not $redisPassword) {
        throw 'Native MySQL test and Redis passwords are required'
    }

    Push-Location $backendRoot
    try {
        & $javaPath "-Dmaven.multiModuleProjectDirectory=$backendRoot" '-classpath' $wrapperJar `
            'org.apache.maven.wrapper.MavenWrapperMain' '-q' 'package'
        if ($LASTEXITCODE -ne 0) {
            throw "Backend package failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
    if (-not (Test-Path -LiteralPath $backendJar -PathType Leaf)) {
        throw 'Backend executable jar was not created'
    }

    $env:SPRING_PROFILES_ACTIVE = 'e2e'
    $env:DB_URL = $testDatabaseUrl
    $env:DB_USERNAME = $testDatabaseUsername
    $env:DB_PASSWORD = $testPassword
    $env:SPRING_DATASOURCE_URL = $testDatabaseUrl
    $env:SPRING_DATASOURCE_USERNAME = $testDatabaseUsername
    $env:SPRING_DATASOURCE_PASSWORD = $testPassword
    $env:REDIS_HOST = '127.0.0.1'
    $env:REDIS_PORT = $redisPort
    $env:REDIS_USERNAME = ''
    $env:REDIS_PASSWORD = $redisPassword
    $env:REDIS_SSL_ENABLED = 'false'
    $env:REDIS_KEY_PREFIX = "news-verification:e2e:$runId"
    $env:REDIS_SESSION_NAMESPACE = "news-verification:e2e:session:$runId"
    $env:HEALTH_ANALYSIS_PROVIDER = 'mock'
    $env:LOOKUP_HMAC_KEY = "full-stack-e2e-lookup-key-$runId"
    $env:INVITE_CODE_1 = $inviteCode
    $env:E2E_SIGNUP_CODE = $verificationCode
    $env:SERVER_PORT = $backendPort
    $env:MANAGEMENT_HEALTH_MAIL_ENABLED = 'false'
    $env:APP_BASE_URL = 'http://127.0.0.1:4173'
    $env:SESSION_COOKIE_SECURE = 'false'
    $env:E2E_USERNAME = $username
    $env:E2E_EMAIL = $email
    $env:E2E_PHONE_NUMBER = $phoneNumber
    $env:E2E_PASSWORD = $password
    $env:E2E_INVITE_CODE = $inviteCode
    $env:E2E_PUBLISHER_NAME = $publisherName
    $env:E2E_ARTICLE_URL = $articleUrl
    $env:E2E_ARTICLE_TITLE = $articleTitle

    $publisherId = Invoke-MySql -Password $testPassword -User $testUser -Database $testDatabase -Sql @"
START TRANSACTION;
INSERT INTO news_publishers (name, category, status)
VALUES ('$publisherName', 'HEALTH_MEDICAL', 'ACTIVE');
SET @publisher_id = LAST_INSERT_ID();
INSERT INTO news_publisher_domains (publisher_id, hostname, status)
VALUES (@publisher_id, '$publisherHost', 'ACTIVE');
SELECT @publisher_id;
COMMIT;
"@
    if ($publisherId -notmatch '^\d+$') {
        throw 'Full-stack Smoke publisher fixture was not created'
    }
    Write-Host '[PASS] Native MySQL Smoke fixture prepared'

    $containerId = & docker run --detach --rm --name $containerName `
        --publish "127.0.0.1:${redisPort}:6379" `
        --env REDIS_PASSWORD `
        --health-cmd 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning ping' `
        --health-interval 2s `
        --health-timeout 2s `
        --health-retries 20 `
        redis:8.8-alpine `
        sh -c 'exec redis-server --bind 0.0.0.0 --protected-mode yes --port 6379 --save "" --appendonly no --maxmemory 64mb --maxmemory-policy noeviction --requirepass "$REDIS_PASSWORD"'
    if ($LASTEXITCODE -ne 0 -or -not $containerId) {
        throw "Redis Smoke container startup failed with exit code $LASTEXITCODE"
    }
    $containerStarted = $true

    $healthDeadline = (Get-Date).AddSeconds(60)
    do {
        $containerHealth = & docker inspect --format '{{.State.Health.Status}}' $containerName
        if ($LASTEXITCODE -ne 0) {
            throw 'Redis Smoke container health inspection failed'
        }
        if ($containerHealth -eq 'healthy') {
            break
        }
        if ($containerHealth -eq 'unhealthy') {
            throw 'Redis Smoke container became unhealthy'
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $healthDeadline)
    if ($containerHealth -ne 'healthy') {
        throw 'Redis Smoke container health check timed out'
    }
    Write-Host '[PASS] Docker Redis Smoke fixture prepared'

    [void](New-Item -ItemType Directory -Path $temporaryDirectory)
    $backendProcess = Start-Process -FilePath $javaPath `
        -ArgumentList @('-jar', $backendJar) `
        -WindowStyle Hidden `
        -PassThru `
        -RedirectStandardOutput (Join-Path $temporaryDirectory 'backend.out.log') `
        -RedirectStandardError (Join-Path $temporaryDirectory 'backend.err.log')
    Wait-BackendReady -Process $backendProcess
    Write-Host '[PASS] Backend e2e Profile ready'

    Push-Location $frontendRoot
    try {
        & npx.cmd playwright test --config=playwright.full-stack.config.ts
        if ($LASTEXITCODE -ne 0) {
            throw "Full-stack Browser Smoke failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }

    Write-Host '[PASS] Frontend, Backend, Native MySQL and Docker Redis Full-stack Smoke E2E'
}
finally {
    if ($backendProcess -and -not $backendProcess.HasExited) {
        Stop-Process -Id $backendProcess.Id
        [void]$backendProcess.WaitForExit(10000)
    }
    if ($containerStarted) {
        & docker stop $containerName | Out-Null
    }
    if ($testPassword -and $publisherId -match '^\d+$') {
        try {
            [void](Invoke-MySql -Password $testPassword -User $testUser -Database $testDatabase -Sql @"
DELETE FROM users WHERE username = '$username';
DELETE FROM news_publisher_domains WHERE hostname = '$publisherHost';
DELETE FROM news_publishers WHERE id = $publisherId;
"@)
        }
        catch {
            Write-Warning 'Full-stack Smoke MySQL fixture cleanup failed'
        }
    }
    $resolvedTemporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $resolvedTemporaryDirectory = [IO.Path]::GetFullPath($temporaryDirectory)
    if ($resolvedTemporaryDirectory.StartsWith($resolvedTemporaryRoot, [StringComparison]::OrdinalIgnoreCase) `
            -and (Test-Path -LiteralPath $resolvedTemporaryDirectory -PathType Container)) {
        Remove-Item -LiteralPath $resolvedTemporaryDirectory -Recurse -Force
    }
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $environmentBackup[$name], 'Process')
    }
    if ($testPasswordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($testPasswordPointer)
    }
    if ($redisPasswordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($redisPasswordPointer)
    }
    $testPassword = $null
    $applicationPassword = $null
    $redisPassword = $null
    $password = $null
    if ($localValues) {
        $localValues.Clear()
    }
}
