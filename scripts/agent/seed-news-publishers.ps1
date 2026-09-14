# 지원 언론사 초기 기준 데이터 적용과 검증
[CmdletBinding()]
param(
    [ValidateSet('Development', 'Test')]
    [string] $Target = 'Development',

    [switch] $VerifyOnly
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$environmentPath = Join-Path $repoRoot '.env'
$seedPath = Join-Path $repoRoot 'infra\mysql\seed\initial-news-publishers.sql'
$mysqlPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'

foreach ($requiredPath in @($environmentPath, $seedPath, $mysqlPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required publisher seed file missing: $requiredPath"
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

function Get-ConfiguredValue {
    param(
        [Parameter(Mandatory)]
        [hashtable] $Values,

        [Parameter(Mandatory)]
        [string] $Name,

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

function Assert-Equal {
    param(
        [Parameter(Mandatory)]
        [string] $Name,

        [Parameter(Mandatory)]
        [int] $Actual,

        [Parameter(Mandatory)]
        [int] $Expected
    )

    if ($Actual -ne $Expected) {
        throw "$Name mismatch: expected $Expected, actual $Actual"
    }
    Write-Host "[PASS] ${Name}: $Actual"
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
        throw 'Test publisher seed requires a separate _test database and account'
    }
    $passwordCandidates = @(
        [Environment]::GetEnvironmentVariable('TEST_DB_PASSWORD', 'Process'),
        (Get-ConfiguredValue -Values $localValues -Name 'MYSQL_TEST_PASSWORD'),
        [Environment]::GetEnvironmentVariable('DB_PASSWORD', 'Process'),
        (Get-ConfiguredValue -Values $localValues -Name 'DB_PASSWORD'),
        (Get-ConfiguredValue -Values $localValues -Name 'MYSQL_PASSWORD')
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
    throw 'Publisher seed must use a DML-only application account'
}

$securePassword = $null
$passwordPointer = [IntPtr]::Zero
$databasePassword = $null

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

    $beforeCounts = (Invoke-MySql -Password $databasePassword -User $databaseUser -Database $databaseName -Sql @'
SELECT (SELECT COUNT(*) FROM news_publishers),
       (SELECT COUNT(*) FROM news_publisher_domains);
'@).Output -split "`t"

    if ($beforeCounts.Count -ne 2) {
        throw 'Publisher seed precondition query returned an unexpected result'
    }

    $publisherCountBefore = [int]$beforeCounts[0]
    $domainCountBefore = [int]$beforeCounts[1]
    if ($VerifyOnly) {
        if ($publisherCountBefore -eq 0 -and $domainCountBefore -eq 0) {
            throw 'Publisher seed data is not applied'
        }
    }
    elseif ($publisherCountBefore -ne 0 -or $domainCountBefore -ne 0) {
        throw 'Publisher seed requires empty news_publishers and news_publisher_domains tables'
    }
    else {
        $seedSql = Get-Content -LiteralPath $seedPath -Raw
        [void](Invoke-MySql -Password $databasePassword -User $databaseUser -Database $databaseName -Sql $seedSql)
        Write-Host "[PASS] Publisher seed applied to $Target database"
    }

    $verification = (Invoke-MySql -Password $databasePassword -User $databaseUser -Database $databaseName -Sql @'
SELECT
    (SELECT COUNT(*) FROM news_publishers),
    (SELECT COUNT(*) FROM news_publishers WHERE status = 'ACTIVE'),
    (SELECT COUNT(*) FROM news_publishers WHERE status = 'CANDIDATE'),
    (SELECT COUNT(*) FROM news_publishers WHERE status IN ('PAUSED_AUTO', 'PAUSED_MANUAL')),
    (SELECT COUNT(*) FROM news_publishers WHERE category = 'NEWS_AGENCY'),
    (SELECT COUNT(*) FROM news_publishers WHERE category = 'BROADCAST_NEWS'),
    (SELECT COUNT(*) FROM news_publishers WHERE category = 'GENERAL_NEWSPAPER'),
    (SELECT COUNT(*) FROM news_publishers WHERE category = 'BUSINESS_NEWSPAPER'),
    (SELECT COUNT(*) FROM news_publishers WHERE category = 'HEALTH_MEDICAL'),
    (SELECT COUNT(*) FROM news_publisher_domains),
    (SELECT COUNT(*) FROM news_publisher_domains WHERE status = 'ACTIVE'),
    (SELECT COUNT(*) FROM news_publisher_domains WHERE status = 'PAUSED'),
    (
        SELECT COUNT(*)
        FROM news_publishers publisher
        WHERE publisher.status = 'ACTIVE'
          AND NOT EXISTS (
              SELECT 1
              FROM news_publisher_domains domain
              WHERE domain.publisher_id = publisher.id
                AND domain.status = 'ACTIVE'
          )
    ),
    (
        SELECT COUNT(*)
        FROM news_publishers publisher
        WHERE publisher.status <> 'ACTIVE'
          AND EXISTS (
              SELECT 1
              FROM news_publisher_domains domain
              WHERE domain.publisher_id = publisher.id
                AND domain.status = 'ACTIVE'
          )
    );
'@).Output -split "`t"

    if ($verification.Count -ne 14) {
        throw 'Publisher seed verification query returned an unexpected result'
    }

    $expectedCounts = @(
        @('Publisher count', 20),
        @('Active publisher count', 9),
        @('Candidate publisher count', 11),
        @('Paused publisher count', 0),
        @('News agency count', 2),
        @('Broadcast news count', 5),
        @('General newspaper count', 8),
        @('Business newspaper count', 2),
        @('Health medical count', 3),
        @('Publisher domain count', 35),
        @('Active domain count', 16),
        @('Paused domain count', 19),
        @('Active publisher without active domain count', 0),
        @('Non-active publisher with active domain count', 0)
    )

    for ($index = 0; $index -lt $expectedCounts.Count; $index++) {
        Assert-Equal -Name $expectedCounts[$index][0] -Actual ([int]$verification[$index]) `
            -Expected $expectedCounts[$index][1]
    }
}
finally {
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    $databasePassword = $null
    $passwordCandidates = $null
    $candidate = $null
    if ($localValues) {
        $localValues.Clear()
    }
}
