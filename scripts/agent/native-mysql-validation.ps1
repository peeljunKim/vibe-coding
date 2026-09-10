# Native MySQL Schema와 테스트 연결 공통 검증
$script:PublisherCategories = @(
    'NEWS_AGENCY'
    'BROADCAST_NEWS'
    'GENERAL_NEWSPAPER'
    'BUSINESS_NEWSPAPER'
    'HEALTH_MEDICAL'
)

function Get-NativeMySqlSchemaFiles {
    param(
        [Parameter(Mandatory)]
        [string] $SchemaDirectory,

        [Parameter(Mandatory)]
        [string] $InitialSchemaPath
    )

    $resolvedSchemaDirectory = (Resolve-Path -LiteralPath $SchemaDirectory).Path
    $resolvedInitialSchemaPath = (Resolve-Path -LiteralPath $InitialSchemaPath).Path
    $schemaFiles = @(Get-ChildItem -LiteralPath $resolvedSchemaDirectory -Filter 'V????__*.sql' -File |
            Sort-Object Name)

    if ($schemaFiles.Count -eq 0 -or $schemaFiles[0].FullName -cne $resolvedInitialSchemaPath) {
        throw 'Initial Native MySQL schema file ordering is invalid'
    }

    $versions = [System.Collections.Generic.HashSet[int]]::new()
    foreach ($schemaFile in $schemaFiles) {
        if ($schemaFile.Name -notmatch '^V(?<Version>\d{4})__[A-Za-z0-9_]+\.sql$') {
            throw 'Native MySQL schema file name is invalid'
        }

        $version = [int] $Matches.Version
        if (-not $versions.Add($version)) {
            throw 'Native MySQL schema versions must be unique'
        }
        if ($version -eq 2) {
            throw 'Native MySQL schema V0002 is retired and must not be reused'
        }
        if ($schemaFile.FullName -cne $resolvedInitialSchemaPath -and $version -lt 3) {
            throw 'Native MySQL follow-up schema versions must start at V0003'
        }
    }

    return $schemaFiles
}

function Assert-PublisherCategorySchemaDefinition {
    param(
        [Parameter(Mandatory)]
        [string] $SchemaSql
    )

    $tableMatch = [regex]::Match(
        $SchemaSql,
        '(?is)CREATE\s+TABLE\s+`?news_publishers`?\s*\((?<Definition>.*?)\)\s*ENGINE\s*=',
        [Text.RegularExpressions.RegexOptions]::CultureInvariant
    )
    if (-not $tableMatch.Success) {
        throw 'news_publishers CREATE TABLE definition is missing'
    }

    $definition = $tableMatch.Groups['Definition'].Value
    if ($definition -notmatch '(?im)^\s*`?category`?\s+VARCHAR\(30\)\s+NOT\s+NULL(?:\s+COMMENT\s+''[^'']*'')?\s*,') {
        throw 'news_publishers.category must be VARCHAR(30) NOT NULL'
    }

    $categoryValues = ($script:PublisherCategories | ForEach-Object { "'$_'" }) -join '\s*,\s*'
    $categoryCheckPattern = [string]::Format(
        '(?is)CONSTRAINT\s+`?ck_news_publishers_category`?\s+CHECK\s*\(\s*`?category`?\s+IN\s*\(\s*{0}\s*\)\s*\)',
        $categoryValues
    )
    if ($definition -notmatch $categoryCheckPattern) {
        throw 'news_publishers.category CHECK must contain exactly the supported category values'
    }
}

function Assert-PublisherCategoryMetadata {
    param(
        [Parameter(Mandatory)]
        [string] $ColumnMetadata,

        [Parameter(Mandatory)]
        [string] $ConstraintMetadata
    )

    $expectedColumnMetadata = "varchar`tvarchar(30)`tNO`t30`tNULL`t3"
    if ($ColumnMetadata -cne $expectedColumnMetadata) {
        throw 'Existing news_publishers.category column metadata is invalid'
    }

    $constraintParts = @($ConstraintMetadata -split "`t", 3)
    if ($constraintParts.Count -ne 3 -or
        $constraintParts[0] -cne 'ck_news_publishers_category' -or
        $constraintParts[1] -cne 'YES') {
        throw 'Existing news_publishers.category CHECK metadata is missing or not enforced'
    }

    $categoryValues = ($script:PublisherCategories | ForEach-Object { "(?:_utf8mb4)?'$_'" }) -join '\s*,\s*'
    $checkClausePattern = [string]::Format(
        '(?is)^\s*\(*\s*`?category`?\s+in\s*\(\s*{0}\s*\)\s*\)*\s*$',
        $categoryValues
    )
    if ($constraintParts[2] -cnotmatch $checkClausePattern) {
        throw 'Existing news_publishers.category CHECK values are invalid'
    }
}

function Assert-NativeMySqlTestConnection {
    param(
        [Parameter(Mandatory)]
        [string] $DatabaseUrl,

        [Parameter(Mandatory)]
        [string] $Username,

        [Parameter(Mandatory)]
        [string] $ExpectedDatabase,

        [Parameter(Mandatory)]
        [string] $ExpectedUsername,

        [Parameter(Mandatory)]
        [string] $DevelopmentDatabase,

        [Parameter(Mandatory)]
        [string] $DevelopmentUsername
    )

    $testNamePattern = '^[A-Za-z0-9_]+_test$'
    if ($ExpectedDatabase -notmatch $testNamePattern -or $ExpectedUsername -notmatch $testNamePattern) {
        throw 'Expected Native MySQL test database and username must end with _test'
    }
    if ($ExpectedDatabase -ieq $DevelopmentDatabase -or $ExpectedUsername -ieq $DevelopmentUsername) {
        throw 'Expected Native MySQL test database and username must differ from development settings'
    }
    if ($Username -notmatch $testNamePattern -or $Username -in @('root', 'dev')) {
        throw 'TEST_DB_USERNAME must be a restricted account whose name ends with _test'
    }
    if ($Username -cne $ExpectedUsername) {
        throw 'TEST_DB_USERNAME does not match MYSQL_TEST_USER'
    }

    $urlMatch = [regex]::Match(
        $DatabaseUrl,
        '^jdbc:mysql://(?<host>localhost|127\.0\.0\.1):3306/(?<database>[A-Za-z0-9_]+_test)\?(?<query>[^?#]+)$',
        [Text.RegularExpressions.RegexOptions]::CultureInvariant
    )
    if (-not $urlMatch.Success) {
        throw 'TEST_DB_URL must use an exact local MySQL test database URL'
    }

    $database = $urlMatch.Groups['database'].Value
    if ($database -cne $ExpectedDatabase -or $database -ieq $DevelopmentDatabase) {
        throw 'TEST_DB_URL does not match MYSQL_TEST_DATABASE'
    }

    $allowedParameters = @{
        useUnicode = 'true'
        characterEncoding = 'utf8'
        serverTimezone = 'UTC'
    }
    $actualParameters = @{}
    foreach ($parameter in $urlMatch.Groups['query'].Value.Split('&')) {
        $parts = $parameter.Split('=')
        if ($parts.Count -ne 2 -or -not $allowedParameters.ContainsKey($parts[0])) {
            throw 'TEST_DB_URL contains an unsupported query parameter'
        }
        if ($actualParameters.ContainsKey($parts[0]) -or $parts[1] -cne $allowedParameters[$parts[0]]) {
            throw 'TEST_DB_URL contains an invalid query parameter value'
        }
        $actualParameters[$parts[0]] = $parts[1]
    }
    if ($actualParameters.Count -ne $allowedParameters.Count) {
        throw 'TEST_DB_URL must contain the configured Unicode, encoding, and timezone parameters'
    }

    return [pscustomobject]@{
        Database = $database
        Username = $Username
    }
}
