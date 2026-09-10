# Native MySQL 테스트 Database와 제한 계정 구성
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$schemaDirectory = Join-Path $repoRoot 'infra\mysql\schema'
$initialSchemaPath = Join-Path $schemaDirectory 'V0001__create_initial_domain_schema.sql'
$validationPath = Join-Path $PSScriptRoot 'native-mysql-validation.ps1'
$environmentPath = Join-Path $repoRoot '.env'
$verificationPath = Join-Path $PSScriptRoot 'verify-publisher-native-mysql.ps1'
$mysqlPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'

foreach ($requiredPath in @($mysqlPath, $environmentPath, $initialSchemaPath, $validationPath, $verificationPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required Native MySQL test setup file missing: $requiredPath"
    }
}

. $validationPath

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

function Set-EnvironmentValue {
    param(
        [Parameter(Mandatory)]
        [string] $Content,

        [Parameter(Mandatory)]
        [string] $Name,

        [Parameter(Mandatory)]
        [string] $Value
    )

    $escapedName = [regex]::Escape($Name)
    if ($Content -match "(?m)^$escapedName=") {
        return [regex]::Replace($Content, "(?m)^$escapedName=.*$", "$Name=$Value")
    }
    return "$($Content.TrimEnd())`r`n$Name=$Value`r`n"
}

function Invoke-MySql {
    param(
        [Parameter(Mandatory)]
        [string] $Password,

        [Parameter(Mandatory)]
        [string] $Sql,

        [Parameter(Mandatory)]
        [string] $User,

        [string] $Database = '',

        [switch] $AllowFailure
    )

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $mysqlPath
    $startInfo.ArgumentList.Add('--host=127.0.0.1')
    $startInfo.ArgumentList.Add('--port=3306')
    $startInfo.ArgumentList.Add("--user=$User")
    if ($Database) {
        $startInfo.ArgumentList.Add("--database=$Database")
    }
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

$localValues = Read-EnvironmentValues -Path $environmentPath
$developmentDatabase = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_DATABASE'
$developmentUser = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_USER'
if (-not $developmentDatabase -or -not $developmentUser) {
    throw 'MYSQL_DATABASE and MYSQL_USER must be configured in ignored .env'
}

$testDatabase = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_TEST_DATABASE' -Fallback "${developmentDatabase}_test"
$testUser = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_TEST_USER' -Fallback "${developmentUser}_test"
$configuredTestPassword = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_TEST_PASSWORD'
$testDatabaseUrl = "jdbc:mysql://127.0.0.1:3306/$testDatabase`?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"

Assert-NativeMySqlTestConnection `
    -DatabaseUrl $testDatabaseUrl `
    -Username $testUser `
    -ExpectedDatabase $testDatabase `
    -ExpectedUsername $testUser `
    -DevelopmentDatabase $developmentDatabase `
    -DevelopmentUsername $developmentUser | Out-Null

$secureRootPassword = Read-Host 'MySQL Root Password' -AsSecureString
$secureTestPassword = $null
if (-not $configuredTestPassword) {
    $secureTestPassword = Read-Host "$testUser Password" -AsSecureString
}
$rootPasswordPointer = [IntPtr]::Zero
$testPasswordPointer = [IntPtr]::Zero
$rootPassword = $null
$testPassword = $configuredTestPassword
$ddlProbeTable = "agent_test_ddl_probe_$PID"
$processEnvironmentNames = @(
    'TEST_DB_URL'
    'TEST_DB_USERNAME'
    'TEST_DB_PASSWORD'
    'MYSQL_TEST_DATABASE'
    'MYSQL_TEST_USER'
    'MYSQL_DATABASE'
    'MYSQL_USER'
)
$processEnvironmentBackup = @{}

try {
    $rootPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureRootPassword)
    $rootPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($rootPasswordPointer)
    if ($secureTestPassword) {
        $testPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureTestPassword)
        $testPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($testPasswordPointer)
    }
    if (-not $testPassword) {
        throw 'Native MySQL test account password is required'
    }

    $version = (Invoke-MySql -Password $rootPassword -User 'root' -Sql 'SELECT VERSION();').Output
    if (-not $version.StartsWith('8.0.30')) {
        throw "MySQL version mismatch: expected 8.0.30, actual $version"
    }
    Write-Host "[PASS] Native MySQL version: $version"

    [void](Invoke-MySql -Password $rootPassword -User 'root' -Sql @"
CREATE DATABASE IF NOT EXISTS $testDatabase
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
"@)

    $existingTablesOutput = (Invoke-MySql -Password $rootPassword -User 'root' -Database $testDatabase -Sql @"
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME;
"@).Output
    $existingTables = @($existingTablesOutput -split "`r?`n" | Where-Object { $_ })

    $schemaFiles = @(Get-NativeMySqlSchemaFiles -SchemaDirectory $schemaDirectory -InitialSchemaPath $initialSchemaPath)
    $schemaSql = @($schemaFiles | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw })
    Assert-PublisherCategorySchemaDefinition -SchemaSql ($schemaSql -join "`n")
    $expectedTables = @(
        [regex]::Matches(($schemaSql -join "`n"), '(?im)^\s*CREATE\s+TABLE\s+`?([a-z0-9_]+)`?\s*\(') |
            ForEach-Object { $_.Groups[1].Value } |
            Sort-Object
    )

    if ($existingTables.Count -eq 0) {
        foreach ($sql in $schemaSql) {
            [void](Invoke-MySql -Password $rootPassword -User 'root' -Database $testDatabase -Sql $sql)
        }
        Write-Host '[PASS] Test database schema versions applied in order'
    }
    else {
        $tableDifference = @(Compare-Object -ReferenceObject $expectedTables -DifferenceObject $existingTables)
        if ($tableDifference.Count -ne 0) {
            throw 'Existing test database table set differs from the current schema'
        }
        Write-Host '[PASS] Existing test database left unchanged for schema validation'
    }

    $publisherCategoryColumn = (Invoke-MySql -Password $rootPassword -User 'root' -Database $testDatabase -Sql @"
SELECT DATA_TYPE,
       COLUMN_TYPE,
       IS_NULLABLE,
       CHARACTER_MAXIMUM_LENGTH,
       IF(COLUMN_DEFAULT IS NULL, 'NULL', COLUMN_DEFAULT),
       ORDINAL_POSITION
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'news_publishers'
  AND COLUMN_NAME = 'category';
"@).Output
    $publisherCategoryConstraint = (Invoke-MySql -Password $rootPassword -User 'root' -Database $testDatabase -Sql @"
SELECT tc.CONSTRAINT_NAME, tc.ENFORCED, cc.CHECK_CLAUSE
FROM information_schema.TABLE_CONSTRAINTS tc
JOIN information_schema.CHECK_CONSTRAINTS cc
  ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
 AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
  AND tc.TABLE_NAME = 'news_publishers'
  AND tc.CONSTRAINT_NAME = 'ck_news_publishers_category'
  AND tc.CONSTRAINT_TYPE = 'CHECK';
"@).Output
    Assert-PublisherCategoryMetadata `
        -ColumnMetadata $publisherCategoryColumn `
        -ConstraintMetadata $publisherCategoryConstraint
    Write-Host '[PASS] Test publisher category schema metadata'

    $sqlTestPassword = $testPassword.Replace("'", "''")
    [void](Invoke-MySql -Password $rootPassword -User 'root' -Sql @"
CREATE USER IF NOT EXISTS '$testUser'@'localhost' IDENTIFIED BY '$sqlTestPassword';
CREATE USER IF NOT EXISTS '$testUser'@'127.0.0.1' IDENTIFIED BY '$sqlTestPassword';
ALTER USER '$testUser'@'localhost' IDENTIFIED BY '$sqlTestPassword';
ALTER USER '$testUser'@'127.0.0.1' IDENTIFIED BY '$sqlTestPassword';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '$testUser'@'localhost';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '$testUser'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE ON $testDatabase.* TO '$testUser'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON $testDatabase.* TO '$testUser'@'127.0.0.1';
"@)

    [void](Invoke-MySql -Password $testPassword -User $testUser -Database $testDatabase -Sql 'SELECT COUNT(*) FROM news_publishers;')
    Write-Host '[PASS] Test account DML connection'

    $ddlAttempt = Invoke-MySql -Password $testPassword -User $testUser -Database $testDatabase `
        -Sql "CREATE TABLE $ddlProbeTable (id INT);" -AllowFailure
    if ($ddlAttempt.ExitCode -eq 0) {
        [void](Invoke-MySql -Password $rootPassword -User 'root' -Database $testDatabase -Sql "DROP TABLE $ddlProbeTable;")
        throw 'Test account unexpectedly has DDL permission'
    }
    Write-Host '[PASS] Test account DDL denied'

    $environmentContent = Get-Content -LiteralPath $environmentPath -Raw
    $environmentContent = Set-EnvironmentValue -Content $environmentContent -Name 'MYSQL_TEST_DATABASE' -Value $testDatabase
    $environmentContent = Set-EnvironmentValue -Content $environmentContent -Name 'MYSQL_TEST_USER' -Value $testUser
    $environmentContent = Set-EnvironmentValue -Content $environmentContent -Name 'TEST_DB_URL' -Value $testDatabaseUrl
    $environmentContent = Set-EnvironmentValue -Content $environmentContent -Name 'TEST_DB_USERNAME' -Value $testUser
    [IO.File]::WriteAllText($environmentPath, $environmentContent, [Text.UTF8Encoding]::new($false))
    Write-Host '[PASS] Non-secret test database settings stored in ignored .env'

    foreach ($name in $processEnvironmentNames) {
        $processEnvironmentBackup[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    }
    $env:TEST_DB_URL = $testDatabaseUrl
    $env:TEST_DB_USERNAME = $testUser
    $env:TEST_DB_PASSWORD = $testPassword
    $env:MYSQL_TEST_DATABASE = $testDatabase
    $env:MYSQL_TEST_USER = $testUser
    $env:MYSQL_DATABASE = $developmentDatabase
    $env:MYSQL_USER = $developmentUser
    & $verificationPath
    if ($LASTEXITCODE -ne 0) {
        throw "Publisher integration verification failed with exit code $LASTEXITCODE"
    }
}
finally {
    foreach ($name in $processEnvironmentNames) {
        if ($processEnvironmentBackup.ContainsKey($name)) {
            [Environment]::SetEnvironmentVariable($name, $processEnvironmentBackup[$name], 'Process')
        }
    }
    if ($rootPasswordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($rootPasswordPointer)
    }
    if ($testPasswordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($testPasswordPointer)
    }
    $rootPassword = $null
    $testPassword = $null
    $configuredTestPassword = $null
    if ($localValues) {
        $localValues.Clear()
    }
}
