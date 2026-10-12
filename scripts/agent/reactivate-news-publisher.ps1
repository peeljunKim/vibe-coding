# 자동 중단 언론사 Local 수동 재활성화
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateLength(1, 100)]
    [string] $PublisherName,

    [ValidateSet('Development', 'Test')]
    [string] $Target = 'Test',

    [switch] $RetestPassed
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$environmentPath = Join-Path $repoRoot '.env'
$utilitiesPath = Join-Path $PSScriptRoot 'script-utilities.ps1'
$mysqlPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'

foreach ($requiredPath in @($environmentPath, $utilitiesPath, $mysqlPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required publisher recovery file missing: $requiredPath"
    }
}

if (-not $RetestPassed) {
    throw 'Publisher recovery requires -RetestPassed after extraction retest succeeds'
}
if ($PublisherName -match '[\x00-\x1F\x7F]') {
    throw 'Publisher name contains unsupported control characters'
}

. $utilitiesPath

function Invoke-MySql {
    param(
        [Parameter(Mandatory)]
        [string] $Password,

        [Parameter(Mandatory)]
        [string] $Sql,

        [Parameter(Mandatory)]
        [string] $User,

        [Parameter(Mandatory)]
        [string] $Database,

        [switch] $AllowFailure
    )

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $mysqlPath
    $startInfo.ArgumentList.Add('--host=127.0.0.1')
    $startInfo.ArgumentList.Add('--port=3306')
    $startInfo.ArgumentList.Add('--default-character-set=utf8mb4')
    $startInfo.ArgumentList.Add("--user=$User")
    $startInfo.ArgumentList.Add("--database=$Database")
    $startInfo.ArgumentList.Add('--batch')
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
    [void]$process.StandardError.ReadToEnd()
    $process.WaitForExit()

    if ($process.ExitCode -ne 0 -and -not $AllowFailure) {
        throw "MySQL command failed with exit code $($process.ExitCode)"
    }

    return [pscustomobject]@{
        ExitCode = $process.ExitCode
        Output = $standardOutput
    }
}

function Invoke-RedisCli {
    param(
        [Parameter(Mandatory)]
        [string] $Password,

        [Parameter(Mandatory)]
        [string[]] $Arguments
    )

    $dockerPath = (Get-Command 'docker.exe' -ErrorAction Stop).Source
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $dockerPath
    foreach ($argument in @(
            'compose',
            '--project-directory', $repoRoot,
            'exec', '-T', '-e', 'REDISCLI_AUTH',
            'redis', 'redis-cli', '--raw'
        ) + $Arguments) {
        $startInfo.ArgumentList.Add($argument)
    }
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    $startInfo.Environment['REDISCLI_AUTH'] = $Password

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void]$process.Start()
    $standardOutput = $process.StandardOutput.ReadToEnd().Trim()
    [void]$process.StandardError.ReadToEnd()
    $process.WaitForExit()

    if ($process.ExitCode -ne 0) {
        throw "Redis command failed with exit code $($process.ExitCode)"
    }
    return $standardOutput
}

function Get-Sha256Hex {
    param(
        [Parameter(Mandatory)]
        [string] $Value
    )

    $digest = [Security.Cryptography.SHA256]::Create()
    try {
        return [Convert]::ToHexString(
            $digest.ComputeHash([Text.Encoding]::UTF8.GetBytes($Value))
        ).ToLowerInvariant()
    }
    finally {
        $digest.Dispose()
    }
}

$localValues = Read-EnvironmentValues -Path $environmentPath
$developmentDatabase = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_DATABASE'
$developmentUser = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_USER'
if (-not $developmentDatabase -or -not $developmentUser) {
    throw 'MYSQL_DATABASE and MYSQL_USER must be configured in ignored .env'
}

if ($Target -eq 'Test') {
    $databaseName = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_TEST_DATABASE' -Fallback "${developmentDatabase}_test"
    $databaseUser = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_TEST_USER' -Fallback "${developmentUser}_test"
    if ($databaseName -eq $developmentDatabase -or $databaseUser -eq $developmentUser `
        -or -not $databaseName.EndsWith('_test') -or -not $databaseUser.EndsWith('_test')) {
        throw 'Test publisher recovery requires a separate _test database and account'
    }
    $passwordCandidates = @(
        [Environment]::GetEnvironmentVariable('TEST_DB_PASSWORD', 'Process'),
        (Get-ConfiguredValue -Values $localValues -Name 'MYSQL_TEST_PASSWORD')
    ) | Where-Object { $_ } | Select-Object -Unique
}
else {
    $databaseName = $developmentDatabase
    $databaseUser = $developmentUser
    $passwordCandidates = @(
        [Environment]::GetEnvironmentVariable('DB_PASSWORD', 'Process'),
        (Get-ConfiguredValue -Values $localValues -Name 'DB_PASSWORD'),
        (Get-ConfiguredValue -Values $localValues -Name 'MYSQL_PASSWORD')
    ) | Where-Object { $_ } | Select-Object -Unique
}

if ($databaseName -notmatch '^[A-Za-z0-9_]+$' -or $databaseUser -notmatch '^[A-Za-z0-9_]+$') {
    throw 'Database name and user may contain only letters, numbers, and underscores'
}
if ($databaseUser -eq 'root') {
    throw 'Publisher recovery must use a DML-only application account'
}

$securePassword = $null
$passwordPointer = [IntPtr]::Zero
$databasePassword = $null
$publisherNameHex = [Convert]::ToHexString([Text.Encoding]::UTF8.GetBytes($PublisherName))
$redisPassword = [Environment]::GetEnvironmentVariable('REDIS_PASSWORD', 'Process')
if (-not $redisPassword) {
    $redisPassword = Get-ConfiguredValue -Values $localValues -Name 'REDIS_PASSWORD'
}
$configuredRedisKeyPrefix = Get-ConfiguredValue -Values $localValues -Name 'REDIS_KEY_PREFIX' -Fallback 'news-verification'
if ($configuredRedisKeyPrefix -notmatch '^[A-Za-z0-9:._-]{1,100}$') {
    throw 'REDIS_KEY_PREFIX contains unsupported characters'
}
$redisKeyPrefix = if ($Target -eq 'Test') { "$configuredRedisKeyPrefix`:test" } else { $configuredRedisKeyPrefix }

try {
    foreach ($candidate in $passwordCandidates) {
        $connection = Invoke-MySql -Password $candidate -User $databaseUser -Database $databaseName `
            -Sql 'SELECT 1;' -AllowFailure
        if ($connection.ExitCode -eq 0 -and $connection.Output -eq '1') {
            $databasePassword = $candidate
            break
        }
    }

    if (-not $databasePassword) {
        $securePassword = Read-Host "$databaseUser Password" -AsSecureString
        $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
        $databasePassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    }

    $version = (Invoke-MySql -Password $databasePassword -User $databaseUser -Database $databaseName `
        -Sql 'SELECT VERSION();').Output
    if (-not $version.StartsWith('8.0.30')) {
        throw "MySQL version mismatch: expected 8.0.30, actual $version"
    }
    Write-Host "[PASS] Native MySQL version: $version"

    $current = (Invoke-MySql -Password $databasePassword -User $databaseUser -Database $databaseName -Sql @"
SELECT id, name, status
FROM news_publishers
WHERE name = CONVERT(0x$publisherNameHex USING utf8mb4);
"@).Output
    if (-not $current) {
        throw "Publisher not found: $PublisherName"
    }

    $currentFields = $current -split "`t"
    if ($currentFields.Count -ne 3) {
        throw 'Publisher recovery query returned an unexpected result'
    }
    $publisherId = [long]$currentFields[0]
    $currentStatus = $currentFields[2]

    Write-Host "[INFO] Target: $Target / $databaseName / $PublisherName"
    Write-Host "[INFO] Current status: $currentStatus"
    if ($currentStatus -ne 'PAUSED_AUTO') {
        throw "Publisher recovery requires PAUSED_AUTO status; actual status: $currentStatus"
    }

    if (-not $redisPassword) {
        throw 'REDIS_PASSWORD must be configured in Process environment or ignored .env'
    }
    if ((Invoke-RedisCli -Password $redisPassword -Arguments @('PING')) -ne 'PONG') {
        throw 'Redis publisher extraction counter cleanup preflight failed'
    }
    $publisherDigest = Get-Sha256Hex -Value $publisherId.ToString()
    $redisBaseKey = "$redisKeyPrefix`:publisher-extraction-health:v1:$publisherDigest"
    [void](Invoke-RedisCli -Password $redisPassword -Arguments @(
            'DEL',
            "$redisBaseKey`:attempts",
            "$redisBaseKey`:failures"
        ))
    $remainingRedisKeys = Invoke-RedisCli -Password $redisPassword -Arguments @(
        'EXISTS',
        "$redisBaseKey`:attempts",
        "$redisBaseKey`:failures"
    )
    if ($remainingRedisKeys -ne '0') {
        throw 'Redis publisher extraction counters were not cleared'
    }
    Write-Host '[PASS] Publisher extraction counters cleared'

    $affectedRows = [int](Invoke-MySql -Password $databasePassword -User $databaseUser -Database $databaseName -Sql @"
UPDATE news_publishers
SET status = 'ACTIVE',
    status_reason = '수동 재활성화: 추출 보완 및 재시험 통과',
    auto_disabled_at = NULL,
    status_changed_at = CURRENT_TIMESTAMP(6),
    version = version + 1
WHERE id = $publisherId
  AND status = 'PAUSED_AUTO';
SELECT ROW_COUNT();
"@).Output
    if ($affectedRows -ne 1) {
        throw 'Publisher status changed concurrently; no recovery was applied'
    }

    $afterStatus = (Invoke-MySql -Password $databasePassword -User $databaseUser -Database $databaseName `
        -Sql "SELECT status FROM news_publishers WHERE id = $publisherId;").Output
    if ($afterStatus -ne 'ACTIVE') {
        throw "Publisher recovery verification failed; actual status: $afterStatus"
    }
    Write-Host "[PASS] After status: $afterStatus"
}
finally {
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    $databasePassword = $null
    $redisPassword = $null
    $passwordCandidates = $null
    $candidate = $null
    if ($localValues) {
        $localValues.Clear()
    }
}
