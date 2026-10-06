# Native MySQL 기존 Database 기준선 정렬
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$environmentPath = Join-Path $repoRoot '.env'
$initialSchemaPath = Join-Path $repoRoot 'infra\mysql\schema\V0001__create_initial_domain_schema.sql'
$validationPath = Join-Path $PSScriptRoot 'native-mysql-validation.ps1'
$utilitiesPath = Join-Path $PSScriptRoot 'script-utilities.ps1'
$mysqlPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$rootPasswordPointer = [IntPtr]::Zero
$rootPassword = $null

foreach ($requiredPath in @($environmentPath, $initialSchemaPath, $validationPath, $utilitiesPath, $mysqlPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required Native MySQL baseline alignment file missing: $requiredPath"
    }
}

. $validationPath
. $utilitiesPath

function Invoke-MySql {
    param(
        [Parameter(Mandatory)]
        [string] $Password,

        [Parameter(Mandatory)]
        [string] $Sql,

        [string] $Database = ''
    )

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $mysqlPath
    $startInfo.ArgumentList.Add('--host=127.0.0.1')
    $startInfo.ArgumentList.Add('--protocol=TCP')
    $startInfo.ArgumentList.Add('--port=3306')
    $startInfo.ArgumentList.Add('--user=root')
    if ($Database) {
        $startInfo.ArgumentList.Add("--database=$Database")
    }
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
    [void]$process.StandardError.ReadToEnd()
    $process.WaitForExit()

    if ($process.ExitCode -ne 0) {
        throw "MySQL baseline alignment command failed with exit code $($process.ExitCode)"
    }
    return $standardOutput
}

function Get-StandardIntegerColumnMetadata {
    param(
        [Parameter(Mandatory)]
        [string] $Password,

        [Parameter(Mandatory)]
        [string] $Database
    )

    return Invoke-MySql -Password $Password -Database $Database -Sql @"
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND (
       (TABLE_NAME = 'users' AND COLUMN_NAME = 'failed_login_count')
    OR (TABLE_NAME = 'health_analysis_records' AND COLUMN_NAME IN ('total_claim_count', 'supported_claim_count'))
    OR (TABLE_NAME = 'health_claims' AND COLUMN_NAME = 'claim_order')
    OR (TABLE_NAME = 'health_claim_evidences' AND COLUMN_NAME = 'evidence_order')
    OR (TABLE_NAME = 'headline_share_issues' AND COLUMN_NAME = 'issue_order')
  )
ORDER BY TABLE_NAME, COLUMN_NAME;
"@
}

function Get-HealthRecordUniqueIndexMetadata {
    param(
        [Parameter(Mandatory)]
        [string] $Password,

        [Parameter(Mandatory)]
        [string] $Database
    )

    return Invoke-MySql -Password $Password -Database $Database -Sql @"
SELECT NON_UNIQUE,
       GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'health_analysis_records'
  AND INDEX_NAME = 'uk_health_records_user_url_analyzed'
GROUP BY INDEX_NAME, NON_UNIQUE;
"@
}

$localValues = Read-EnvironmentValues -Path $environmentPath
$developmentDatabase = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_DATABASE'
$testDatabase = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_TEST_DATABASE' -Fallback "${developmentDatabase}_test"
if ($developmentDatabase -notmatch '^[A-Za-z0-9_]+$' -or
    $testDatabase -notmatch '^[A-Za-z0-9_]+$' -or
    $testDatabase -ceq $developmentDatabase -or
    $testDatabase -notmatch '_test$') {
    throw 'Development and test database names are missing or unsafe'
}

$schemaSql = Get-Content -LiteralPath $initialSchemaPath -Raw
Assert-StandardIntegerSchemaDefinition -SchemaSql $schemaSql
Assert-HealthRecordUniqueIndexSchemaDefinition -SchemaSql $schemaSql

$secureRootPassword = Read-Host 'MySQL Root Password' -AsSecureString
try {
    $rootPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureRootPassword)
    $rootPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($rootPasswordPointer)

    foreach ($database in @($developmentDatabase, $testDatabase)) {
        $databaseExists = Invoke-MySql -Password $rootPassword -Sql @"
SELECT COUNT(*)
FROM information_schema.SCHEMATA
WHERE SCHEMA_NAME = '$database';
"@
        if ([int]$databaseExists -ne 1) {
            throw "Expected Native MySQL database is missing: $database"
        }

        [void](Invoke-MySql -Password $rootPassword -Database $database -Sql @"
ALTER TABLE users
  MODIFY failed_login_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '연속 로그인 실패 횟수';
ALTER TABLE health_analysis_records
  MODIFY total_claim_count INT UNSIGNED NOT NULL COMMENT '추출한 핵심 주장 수',
  MODIFY supported_claim_count INT UNSIGNED NOT NULL COMMENT '근거 있음 주장 수';
ALTER TABLE health_claims
  MODIFY claim_order INT UNSIGNED NOT NULL COMMENT '중요도 순서 1부터 3';
ALTER TABLE health_claim_evidences
  MODIFY evidence_order INT UNSIGNED NOT NULL COMMENT '주장 내 표시 순서';
ALTER TABLE headline_share_issues
  MODIFY issue_order INT UNSIGNED NOT NULL COMMENT '표시 순서';
"@)

        $uniqueIndexMetadata = Get-HealthRecordUniqueIndexMetadata -Password $rootPassword -Database $database
        if (-not $uniqueIndexMetadata) {
            $duplicateGroupCount = Invoke-MySql -Password $rootPassword -Database $database -Sql @"
SELECT COUNT(*)
FROM (
    SELECT user_id, normalized_url_digest, analyzed_at
    FROM health_analysis_records
    GROUP BY user_id, normalized_url_digest, analyzed_at
    HAVING COUNT(*) > 1
) duplicate_health_records;
"@
            if ([int]$duplicateGroupCount -ne 0) {
                throw "Duplicate health analysis records must be resolved before baseline alignment: $database"
            }
            [void](Invoke-MySql -Password $rootPassword -Database $database -Sql @"
ALTER TABLE health_analysis_records
  ADD CONSTRAINT uk_health_records_user_url_analyzed
  UNIQUE (user_id, normalized_url_digest, analyzed_at);
"@)
            $uniqueIndexMetadata = Get-HealthRecordUniqueIndexMetadata -Password $rootPassword -Database $database
        }

        Assert-StandardIntegerColumnMetadata `
            -ColumnMetadata (Get-StandardIntegerColumnMetadata -Password $rootPassword -Database $database)
        Assert-HealthRecordUniqueIndexMetadata -IndexMetadata $uniqueIndexMetadata
        Write-Host "[PASS] Native MySQL baseline aligned: $database"
    }
}
finally {
    if ($rootPasswordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($rootPasswordPointer)
    }
    $rootPassword = $null
    $secureRootPassword = $null
}
