# Native MySQL 초기 Schema 적용과 JPA 기동 검증
[CmdletBinding()]
param(
    [ValidateRange(10, 120)]
    [int] $StartupTimeoutSeconds = 60
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$backendRoot = Join-Path $repoRoot 'backend'
$schemaDirectory = Join-Path $repoRoot 'infra\mysql\schema'
$initialSchemaPath = Join-Path $schemaDirectory 'V0001__create_initial_domain_schema.sql'
$validationPath = Join-Path $PSScriptRoot 'native-mysql-validation.ps1'
$environmentTemplatePath = Join-Path $repoRoot '.env.example'
$environmentPath = Join-Path $repoRoot '.env'
$mysqlPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$javaPath = 'C:\Program Files\Java\jdk-17\bin\java.exe'
$wrapperJar = Join-Path $backendRoot '.mvn\wrapper\maven-wrapper.jar'
$rootPasswordPointer = [IntPtr]::Zero
$appPasswordPointer = [IntPtr]::Zero
$rootPassword = $null
$appPassword = $null
$appProcess = $null
$ddlProbeTable = "agent_ddl_probe_$PID"
$stdoutPath = Join-Path ([IO.Path]::GetTempPath()) "news-verification-jpa-$PID.stdout.log"
$stderrPath = Join-Path ([IO.Path]::GetTempPath()) "news-verification-jpa-$PID.stderr.log"

foreach ($requiredPath in @($mysqlPath, $javaPath, $wrapperJar, $initialSchemaPath, $validationPath, $environmentTemplatePath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required verification file missing: $requiredPath"
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
        if ($line -match '^(?<Name>[A-Z0-9_]+)=(?<Value>.*)$') {
            $values[$Matches.Name] = $Matches.Value
        }
    }
    return $values
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

        [string] $Database,

        [switch] $AllowFailure
    )

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $mysqlPath
    $startInfo.ArgumentList.Add('--host=localhost')
    $startInfo.ArgumentList.Add('--protocol=TCP')
    $startInfo.ArgumentList.Add('--port=3306')
    $startInfo.ArgumentList.Add("--user=$User")
    $startInfo.ArgumentList.Add('--batch')
    $startInfo.ArgumentList.Add('--skip-column-names')
    if ($Database) {
        $startInfo.ArgumentList.Add("--database=$Database")
    }
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    $startInfo.Environment['MYSQL_PWD'] = $Password

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void] $process.Start()
    $process.StandardInput.Write($Sql)
    $process.StandardInput.Close()
    $standardOutput = $process.StandardOutput.ReadToEnd().Trim()
    $standardError = $process.StandardError.ReadToEnd().Trim()
    $process.WaitForExit()

    if ($process.ExitCode -ne 0 -and -not $AllowFailure) {
        throw "MySQL command failed: $standardError"
    }

    return [pscustomobject]@{
        ExitCode = $process.ExitCode
        Output = $standardOutput
        Error = $standardError
    }
}

function Assert-Equal {
    param(
        [Parameter(Mandatory)]
        [object] $Actual,

        [Parameter(Mandatory)]
        [object] $Expected,

        [Parameter(Mandatory)]
        [string] $Name
    )

    if ($Actual -ne $Expected) {
        throw "$Name mismatch: expected $Expected, actual $Actual"
    }
    Write-Host "[PASS] $Name`: $Actual"
}

$defaultValues = Read-EnvironmentValues -Path $environmentTemplatePath
$localValues = if (Test-Path -LiteralPath $environmentPath -PathType Leaf) {
    Read-EnvironmentValues -Path $environmentPath
}
else {
    @{}
}
$defaultDatabaseName = if ($localValues.MYSQL_DATABASE) { $localValues.MYSQL_DATABASE } else { $defaultValues.MYSQL_DATABASE }
$defaultAppUser = if ($localValues.MYSQL_USER) { $localValues.MYSQL_USER } else { $defaultValues.MYSQL_USER }
$databaseName = $defaultDatabaseName
$appUser = $defaultAppUser

if ($databaseName -notmatch '^[A-Za-z0-9_]+$' -or $appUser -notmatch '^[A-Za-z0-9_]+$') {
    throw 'Database name and user may contain only letters, numbers, and underscores'
}

$secureRootPassword = Read-Host 'MySQL Root Password' -AsSecureString
$secureAppPassword = $null
$configuredAppPassword = $localValues.MYSQL_PASSWORD
$usingConfiguredAppPassword = $configuredAppPassword -and $configuredAppPassword -notmatch '^replace-with-'
if ($usingConfiguredAppPassword) {
    $appPassword = $configuredAppPassword
}
else {
    $secureAppPassword = Read-Host "$appUser Password" -AsSecureString
}
$schemaFiles = @(Get-NativeMySqlSchemaFiles -SchemaDirectory $schemaDirectory -InitialSchemaPath $initialSchemaPath)
$schemaSql = @($schemaFiles | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw })
$schemaDefinitionSql = $schemaSql -join "`n"
Assert-PublisherCategorySchemaDefinition -SchemaSql $schemaDefinitionSql
$expectedTables = @(
    [regex]::Matches($schemaDefinitionSql, '(?im)^\s*CREATE\s+TABLE\s+`?([a-z0-9_]+)`?\s*\(') |
        ForEach-Object { $_.Groups[1].Value } |
        Sort-Object
)
$expectedForeignKeys = [regex]::Matches($schemaDefinitionSql, '(?im)^\s*CONSTRAINT\s+\S+\s+FOREIGN\s+KEY').Count
$expectedChecks = [regex]::Matches($schemaDefinitionSql, '(?im)^\s*(?:ADD\s+)?CONSTRAINT\s+\S+\s+CHECK').Count
$expectedSecondaryIndexes = [regex]::Matches($schemaDefinitionSql, '(?im)^\s*(?:UNIQUE\s+)?KEY\s+').Count

try {
    if ($secureAppPassword) {
        $appPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureAppPassword)
        $appPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($appPasswordPointer)
    }
    $rootPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureRootPassword)
    $rootPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($rootPasswordPointer)
    $sqlAppPassword = $appPassword.Replace("'", "''")

    $version = (Invoke-MySql -Password $rootPassword -User 'root' -Sql 'SELECT VERSION();').Output
    if (-not $version.StartsWith('8.0.30')) {
        throw "MySQL version mismatch: expected 8.0.30, actual $version"
    }
    Write-Host "[PASS] Native MySQL version: $version"

    $databaseExists = [int] (Invoke-MySql -Password $rootPassword -User 'root' -Sql @"
SELECT COUNT(*)
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME = '$databaseName';
"@).Output
    if ($databaseExists -eq 0) {
        [void] (Invoke-MySql -Password $rootPassword -User 'root' -Sql @"
CREATE DATABASE $databaseName CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
"@)
        Write-Host "[PASS] Empty database created: $databaseName"
    }

    $existingTablesOutput = (Invoke-MySql -Password $rootPassword -User 'root' -Database $databaseName -Sql @"
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME;
"@).Output
    $existingTables = @($existingTablesOutput -split "`r?`n" | Where-Object { $_ })
    $schemaAlreadyApplied = $existingTables.Count -gt 0
    if ($schemaAlreadyApplied) {
        $existingTableDifference = @(Compare-Object -ReferenceObject $expectedTables -DifferenceObject $existingTables)
        if ($existingTableDifference.Count -ne 0) {
            throw "Existing table set is not the expected initial schema: $($existingTableDifference | Out-String)"
        }
        Assert-Equal -Name 'Existing initial schema table count' -Actual $existingTables.Count -Expected $expectedTables.Count
    }
    else {
        Assert-Equal -Name 'Pre-application table count' -Actual $existingTables.Count -Expected 0
    }

    if (-not $schemaAlreadyApplied) {
        foreach ($sql in $schemaSql) {
            [void] (Invoke-MySql -Password $rootPassword -User 'root' -Database $databaseName -Sql $sql)
        }
        Write-Host '[PASS] Schema versions applied in order'
    }
    else {
        Write-Host '[PASS] Existing database left unchanged for schema validation'
    }

    $publisherCategoryColumn = (Invoke-MySql -Password $rootPassword -User 'root' -Database $databaseName -Sql @"
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
    $publisherCategoryConstraint = (Invoke-MySql -Password $rootPassword -User 'root' -Database $databaseName -Sql @"
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
    Write-Host '[PASS] Publisher category schema metadata'

    $accountSql = @"
CREATE USER IF NOT EXISTS '$appUser'@'localhost' IDENTIFIED BY '$sqlAppPassword';
CREATE USER IF NOT EXISTS '$appUser'@'127.0.0.1' IDENTIFIED BY '$sqlAppPassword';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '$appUser'@'localhost';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '$appUser'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE ON $databaseName.* TO '$appUser'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON $databaseName.* TO '$appUser'@'127.0.0.1';
"@
    [void] (Invoke-MySql -Password $rootPassword -User 'root' -Sql $accountSql)
    Write-Host "[PASS] Application account prepared: $appUser"

    $actualTablesOutput = (Invoke-MySql -Password $rootPassword -User 'root' -Database $databaseName -Sql @"
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME;
"@).Output
    $actualTables = @($actualTablesOutput -split "`r?`n" | Where-Object { $_ })
    $tableDifference = @(Compare-Object -ReferenceObject $expectedTables -DifferenceObject $actualTables)
    if ($tableDifference.Count -ne 0) {
        throw "Table set mismatch: $($tableDifference | Out-String)"
    }
    Assert-Equal -Name 'Table count' -Actual $actualTables.Count -Expected $expectedTables.Count

    $foreignKeyCount = [int] (Invoke-MySql -Password $rootPassword -User 'root' -Database $databaseName -Sql @"
SELECT COUNT(*)
FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_TYPE = 'FOREIGN KEY';
"@).Output
    Assert-Equal -Name 'Foreign key count' -Actual $foreignKeyCount -Expected $expectedForeignKeys

    $checkCount = [int] (Invoke-MySql -Password $rootPassword -User 'root' -Database $databaseName -Sql @"
SELECT COUNT(*)
FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_TYPE = 'CHECK';
"@).Output
    Assert-Equal -Name 'Check constraint count' -Actual $checkCount -Expected $expectedChecks

    $secondaryIndexCount = [int] (Invoke-MySql -Password $rootPassword -User 'root' -Database $databaseName -Sql @"
SELECT COUNT(*)
FROM (
    SELECT DISTINCT TABLE_NAME, INDEX_NAME
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND INDEX_NAME <> 'PRIMARY'
) indexes_for_verification;
"@).Output
    Assert-Equal -Name 'Secondary index count' -Actual $secondaryIndexCount -Expected $expectedSecondaryIndexes

    $invalidTableOptions = [int] (Invoke-MySql -Password $rootPassword -User 'root' -Database $databaseName -Sql @"
SELECT COUNT(*)
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND (ENGINE <> 'InnoDB' OR TABLE_COLLATION <> 'utf8mb4_0900_ai_ci');
"@).Output
    Assert-Equal -Name 'Invalid engine or collation count' -Actual $invalidTableOptions -Expected 0

    [void] (Invoke-MySql -Password $appPassword -User $appUser -Database $databaseName -Sql 'SELECT COUNT(*) FROM users;')
    Write-Host '[PASS] Application account DML connection'

    $ddlAttempt = Invoke-MySql -Password $appPassword -User $appUser -Database $databaseName -Sql "CREATE TABLE $ddlProbeTable (id INT);" -AllowFailure
    if ($ddlAttempt.ExitCode -eq 0) {
        [void] (Invoke-MySql -Password $rootPassword -User 'root' -Database $databaseName -Sql "DROP TABLE $ddlProbeTable;")
        throw 'Application account unexpectedly has DDL permission'
    }
    Write-Host '[PASS] Application account DDL denied'

    $environmentContent = if (Test-Path -LiteralPath $environmentPath -PathType Leaf) {
        Get-Content -LiteralPath $environmentPath -Raw
    }
    else {
        Get-Content -LiteralPath $environmentTemplatePath -Raw
    }
    $environmentContent = Set-EnvironmentValue -Content $environmentContent -Name 'MYSQL_DATABASE' -Value $databaseName
    $environmentContent = Set-EnvironmentValue -Content $environmentContent -Name 'MYSQL_USER' -Value $appUser
    $environmentContent = Set-EnvironmentValue -Content $environmentContent -Name 'DB_URL' -Value "jdbc:mysql://localhost:3306/$databaseName`?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"
    $environmentContent = Set-EnvironmentValue -Content $environmentContent -Name 'DB_USERNAME' -Value $appUser
    if ($usingConfiguredAppPassword) {
        $environmentContent = Set-EnvironmentValue -Content $environmentContent -Name 'MYSQL_PASSWORD' -Value $appPassword
        $environmentContent = Set-EnvironmentValue -Content $environmentContent -Name 'DB_PASSWORD' -Value $appPassword
    }
    else {
        $environmentContent = Set-EnvironmentValue -Content $environmentContent -Name 'MYSQL_PASSWORD' -Value ''
        $environmentContent = Set-EnvironmentValue -Content $environmentContent -Name 'DB_PASSWORD' -Value ''
    }
    [IO.File]::WriteAllText($environmentPath, $environmentContent, [Text.UTF8Encoding]::new($false))
    Write-Host '[PASS] Local non-secret database settings stored in ignored .env'
    if ($usingConfiguredAppPassword) {
        Write-Host '[PASS] Existing application password retained in ignored .env'
    }
    else {
        Write-Host '[PASS] Prompted application password not stored'
    }

    $environmentNames = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD')
    $environmentBackup = @{}
    foreach ($name in $environmentNames) {
        $environmentBackup[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    }

    try {
        $env:DB_URL = "jdbc:mysql://localhost:3306/$databaseName`?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"
        $env:DB_USERNAME = $appUser
        $env:DB_PASSWORD = $appPassword

        Push-Location $backendRoot
        try {
            & $javaPath "-Dmaven.multiModuleProjectDirectory=$backendRoot" '-classpath' $wrapperJar 'org.apache.maven.wrapper.MavenWrapperMain' '-q' '-DskipTests' 'package'
            if ($LASTEXITCODE -ne 0) {
                throw "Backend package failed with exit code $LASTEXITCODE"
            }
        }
        finally {
            Pop-Location
        }

        $applicationJar = Get-ChildItem -LiteralPath (Join-Path $backendRoot 'target') -Filter '*.jar' |
            Where-Object { $_.Name -notlike '*.original' } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if (-not $applicationJar) {
            throw 'Backend application JAR missing'
        }

        $appProcess = Start-Process -FilePath $javaPath -ArgumentList @(
            '-jar',
            $applicationJar.FullName,
            '--server.port=0',
            '--spring.main.banner-mode=off'
        ) -PassThru -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath -WindowStyle Hidden

        $deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
        $started = $false
        while ([DateTime]::UtcNow -lt $deadline -and -not $appProcess.HasExited) {
            if (Test-Path -LiteralPath $stdoutPath -PathType Leaf) {
                $applicationOutput = Get-Content -LiteralPath $stdoutPath -Raw
                if ($applicationOutput -match 'Started NewsVerificationApplication') {
                    $started = $true
                    break
                }
            }
            Start-Sleep -Milliseconds 500
        }

        if (-not $started) {
            $applicationOutput = if (Test-Path -LiteralPath $stdoutPath -PathType Leaf) {
                Get-Content -LiteralPath $stdoutPath -Raw
            }
            else {
                ''
            }
            $applicationError = if (Test-Path -LiteralPath $stderrPath -PathType Leaf) {
                Get-Content -LiteralPath $stderrPath -Raw
            }
            else {
                ''
            }
            throw "JPA application startup failed:`n$applicationOutput`n$applicationError"
        }
        Write-Host '[PASS] Spring JPA ddl-auto validate startup'
    }
    finally {
        if ($appProcess -and -not $appProcess.HasExited) {
            Stop-Process -Id $appProcess.Id -Force
            $appProcess.WaitForExit()
        }
        foreach ($name in $environmentNames) {
            [Environment]::SetEnvironmentVariable($name, $environmentBackup[$name], 'Process')
        }
    }
}
finally {
    if ($appProcess -and -not $appProcess.HasExited) {
        Stop-Process -Id $appProcess.Id -Force
    }
    if ($rootPasswordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($rootPasswordPointer)
    }
    if ($appPasswordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($appPasswordPointer)
    }
    $rootPassword = $null
    $appPassword = $null
    $configuredAppPassword = $null
    $usingConfiguredAppPassword = $false
    if ($localValues) {
        $localValues.Clear()
    }
    foreach ($logPath in @($stdoutPath, $stderrPath)) {
        if (Test-Path -LiteralPath $logPath -PathType Leaf) {
            Remove-Item -LiteralPath $logPath -Force
        }
    }
}
