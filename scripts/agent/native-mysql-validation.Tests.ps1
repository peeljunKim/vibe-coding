# Native MySQL Schema와 테스트 연결 격리 회귀 테스트
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$validationScript = Join-Path $PSScriptRoot 'native-mysql-validation.ps1'
. $validationScript

$failures = [System.Collections.Generic.List[string]]::new()

function Assert-Throws {
    param(
        [Parameter(Mandatory)]
        [string] $Name,

        [Parameter(Mandatory)]
        [scriptblock] $Action
    )

    try {
        & $Action
        $failures.Add("$Name`: expected an exception")
    }
    catch {
        return
    }
}

$validConnection = Assert-NativeMySqlTestConnection `
    -DatabaseUrl 'jdbc:mysql://127.0.0.1:3306/fixture_app_test?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC' `
    -Username 'fixture_app_test' `
    -ExpectedDatabase 'fixture_app_test' `
    -ExpectedUsername 'fixture_app_test' `
    -DevelopmentDatabase 'fixture_app' `
    -DevelopmentUsername 'fixture_app'

if ($validConnection.Database -ne 'fixture_app_test' -or $validConnection.Username -ne 'fixture_app_test') {
    $failures.Add('valid isolated connection did not return the exact database and username')
}

$invalidConnections = @(
    @('remote host', 'jdbc:mysql://db.example.com:3306/fixture_app_test?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC', 'fixture_app_test'),
    @('wrong database', 'jdbc:mysql://localhost:3306/other_test?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC', 'fixture_app_test'),
    @('database query bypass', 'jdbc:mysql://localhost:3306/fixture_app?databaseName=fixture_app_test&useUnicode=true&characterEncoding=utf8&serverTimezone=UTC', 'fixture_app_test'),
    @('encoded database', 'jdbc:mysql://localhost:3306/fixture_app%5ftest?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC', 'fixture_app_test'),
    @('dangerous properties transform', 'jdbc:mysql://localhost:3306/fixture_app_test?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&propertiesTransform=example.Redirect', 'fixture_app_test'),
    @('dangerous socket factory', 'jdbc:mysql://localhost:3306/fixture_app_test?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&socketFactory=example.SocketFactory', 'fixture_app_test'),
    @('secret query is not echoed', 'jdbc:mysql://localhost:3306/fixture_app_test?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&password=do-not-echo', 'fixture_app_test'),
    @('malformed query', 'jdbc:mysql://localhost:3306/fixture_app_test?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&propertiesTransform', 'fixture_app_test'),
    @('development username', 'jdbc:mysql://localhost:3306/fixture_app_test?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC', 'fixture_app'),
    @('root username', 'jdbc:mysql://localhost:3306/fixture_app_test?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC', 'root'),
    @('unexpected test username', 'jdbc:mysql://localhost:3306/fixture_app_test?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC', 'other_test')
)

foreach ($invalidConnection in $invalidConnections) {
    Assert-Throws -Name $invalidConnection[0] -Action {
        Assert-NativeMySqlTestConnection `
            -DatabaseUrl $invalidConnection[1] `
            -Username $invalidConnection[2] `
            -ExpectedDatabase 'fixture_app_test' `
            -ExpectedUsername 'fixture_app_test' `
            -DevelopmentDatabase 'fixture_app' `
            -DevelopmentUsername 'fixture_app'
    }
}

$schemaTestRoot = Join-Path ([IO.Path]::GetTempPath()) "native-mysql-schema-tests-$([guid]::NewGuid())"
try {
    New-Item -ItemType Directory -Path $schemaTestRoot | Out-Null
    $initialSchemaPath = Join-Path $schemaTestRoot 'V0001__create_initial_domain_schema.sql'
    $followupSchemaPath = Join-Path $schemaTestRoot 'V0003__next_change.sql'
    Set-Content -LiteralPath $initialSchemaPath -Value '-- initial'
    Set-Content -LiteralPath $followupSchemaPath -Value '-- followup'

    $schemaFiles = @(Get-NativeMySqlSchemaFiles -SchemaDirectory $schemaTestRoot -InitialSchemaPath $initialSchemaPath)
    if (($schemaFiles.Name -join ',') -ne 'V0001__create_initial_domain_schema.sql,V0003__next_change.sql') {
        $failures.Add("schema files were not returned in initial-then-version order: $($schemaFiles.Name -join ',')")
    }

    Set-Content -LiteralPath (Join-Path $schemaTestRoot 'V0002__retired.sql') -Value '-- retired'
    Assert-Throws -Name 'retired V0002 is rejected' -Action {
        Get-NativeMySqlSchemaFiles -SchemaDirectory $schemaTestRoot -InitialSchemaPath $initialSchemaPath
    }
}
finally {
    if (Test-Path -LiteralPath $schemaTestRoot) {
        $resolvedTempRoot = (Resolve-Path -LiteralPath ([IO.Path]::GetTempPath())).Path.TrimEnd(
            [IO.Path]::DirectorySeparatorChar,
            [IO.Path]::AltDirectorySeparatorChar
        )
        $resolvedSchemaTestRoot = (Resolve-Path -LiteralPath $schemaTestRoot).Path
        $expectedTestRootPrefix = [IO.Path]::Combine($resolvedTempRoot, 'native-mysql-schema-tests-')
        if (-not $resolvedSchemaTestRoot.StartsWith($expectedTestRootPrefix, [StringComparison]::OrdinalIgnoreCase) -or
            (Split-Path -Parent $resolvedSchemaTestRoot) -ne $resolvedTempRoot) {
            throw 'Schema test cleanup target is outside the intended temporary directory'
        }
        Remove-Item -LiteralPath $resolvedSchemaTestRoot -Recurse -Force
    }
}

$publisherCategoryDefinition = @"
CREATE TABLE news_publishers (
    id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL COMMENT '언론사 분류',
    CONSTRAINT ck_news_publishers_category CHECK (
        category IN (
            'NEWS_AGENCY',
            'BROADCAST_NEWS',
            'GENERAL_NEWSPAPER',
            'BUSINESS_NEWSPAPER',
            'HEALTH_MEDICAL'
        )
    ),
    CONSTRAINT ck_news_publishers_status CHECK (status IN ('ACTIVE'))
) ENGINE=InnoDB;
"@

Assert-PublisherCategorySchemaDefinition -SchemaSql $publisherCategoryDefinition
$validConstraintMetadata = 'ck_news_publishers_category' + "`tYES`t" +
    "((``category`` in (_utf8mb4'NEWS_AGENCY',_utf8mb4'BROADCAST_NEWS',_utf8mb4'GENERAL_NEWSPAPER',_utf8mb4'BUSINESS_NEWSPAPER',_utf8mb4'HEALTH_MEDICAL')))"
Assert-PublisherCategoryMetadata `
    -ColumnMetadata "varchar`tvarchar(30)`tNO`t30`tNULL`t3" `
    -ConstraintMetadata $validConstraintMetadata

Assert-Throws -Name 'old initial schema without publisher category is rejected' -Action {
    Assert-PublisherCategorySchemaDefinition -SchemaSql ($publisherCategoryDefinition -replace '(?m)^\s*category VARCHAR\(30\).+\r?\n', '')
}
Assert-Throws -Name 'disabled publisher category check is rejected' -Action {
    Assert-PublisherCategoryMetadata `
        -ColumnMetadata "varchar`tvarchar(30)`tNO`t30`tNULL`t3" `
        -ConstraintMetadata "ck_news_publishers_category`tNO`t(category in ('NEWS_AGENCY','BROADCAST_NEWS','GENERAL_NEWSPAPER','BUSINESS_NEWSPAPER','HEALTH_MEDICAL'))"
}
Assert-Throws -Name 'missing publisher category check is rejected' -Action {
    Assert-PublisherCategoryMetadata `
        -ColumnMetadata "varchar`tvarchar(30)`tNO`t30`tNULL`t3" `
        -ConstraintMetadata "`t`t"
}
Assert-Throws -Name 'nullable publisher category is rejected' -Action {
    Assert-PublisherCategoryMetadata `
        -ColumnMetadata "varchar`tvarchar(30)`tYES`t30`tNULL`t3" `
        -ConstraintMetadata $validConstraintMetadata
}
Assert-Throws -Name 'publisher category check with an extra value is rejected' -Action {
    Assert-PublisherCategoryMetadata `
        -ColumnMetadata "varchar`tvarchar(30)`tNO`t30`tNULL`t3" `
        -ConstraintMetadata "ck_news_publishers_category`tYES`t(category in ('NEWS_AGENCY','BROADCAST_NEWS','GENERAL_NEWSPAPER','BUSINESS_NEWSPAPER','HEALTH_MEDICAL','OTHER'))"
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host '[PASS] Native MySQL validation regression tests'
