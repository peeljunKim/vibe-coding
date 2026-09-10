# Native MySQL 지원 언론사 Repository 통합 테스트 실행
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$backendRoot = Join-Path $repoRoot 'backend'
$environmentPath = Join-Path $repoRoot '.env'
$javaPath = 'C:\Program Files\Java\jdk-17\bin\java.exe'
$wrapperJar = Join-Path $backendRoot '.mvn\wrapper\maven-wrapper.jar'

foreach ($requiredPath in @($environmentPath, $javaPath, $wrapperJar)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required integration test file missing: $requiredPath"
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

$localValues = Read-EnvironmentValues -Path $environmentPath
$developmentDatabase = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_DATABASE'
$developmentUser = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_USER'
if (-not $developmentDatabase -or -not $developmentUser) {
    throw 'MYSQL_DATABASE and MYSQL_USER must be configured in ignored .env'
}

$testDatabase = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_TEST_DATABASE' -Fallback "${developmentDatabase}_test"
$testUser = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_TEST_USER' -Fallback "${developmentUser}_test"
$testDatabaseUrl = Get-ConfiguredValue -Values $localValues -Name 'TEST_DB_URL' -Fallback "jdbc:mysql://127.0.0.1:3306/$testDatabase`?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"
$testDatabaseUsername = Get-ConfiguredValue -Values $localValues -Name 'TEST_DB_USERNAME' -Fallback $testUser
$configuredTestPassword = Get-ConfiguredValue -Values $localValues -Name 'MYSQL_TEST_PASSWORD'

if ($testDatabase -notmatch '^[A-Za-z0-9_]+_test$' -or $testUser -notmatch '^[A-Za-z0-9_]+_test$') {
    throw 'Test database and user names must end with _test'
}
if ($testDatabase -eq $developmentDatabase -or $testUser -eq $developmentUser) {
    throw 'Native MySQL integration tests must not use the development database or account'
}
if ($testDatabaseUrl -notmatch "/$([regex]::Escape($testDatabase))(?:\?|$)") {
    throw 'TEST_DB_URL does not match MYSQL_TEST_DATABASE'
}

$secureTestPassword = $null
$testPasswordPointer = [IntPtr]::Zero
$testPassword = [Environment]::GetEnvironmentVariable('TEST_DB_PASSWORD', 'Process')
if (-not $testPassword) {
    $testPassword = $configuredTestPassword
}
if (-not $testPassword) {
    $secureTestPassword = Read-Host "$testUser Password" -AsSecureString
}

$environmentNames = @('TEST_DB_URL', 'TEST_DB_USERNAME', 'TEST_DB_PASSWORD')
$environmentBackup = @{}
foreach ($name in $environmentNames) {
    $environmentBackup[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    if ($secureTestPassword) {
        $testPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureTestPassword)
        $testPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($testPasswordPointer)
    }
    if (-not $testPassword) {
        throw 'Native MySQL test account password is required'
    }

    $env:TEST_DB_URL = $testDatabaseUrl
    $env:TEST_DB_USERNAME = $testDatabaseUsername
    $env:TEST_DB_PASSWORD = $testPassword

    Push-Location $backendRoot
    try {
        & $javaPath "-Dmaven.multiModuleProjectDirectory=$backendRoot" '-classpath' $wrapperJar `
            'org.apache.maven.wrapper.MavenWrapperMain' '-q' '-Dtest=NewsPublisherRepositoryIT' 'test'
        if ($LASTEXITCODE -ne 0) {
            throw "Publisher Native MySQL integration test failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }

    Write-Host '[PASS] Publisher Repository Native MySQL integration test'
}
finally {
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $environmentBackup[$name], 'Process')
    }
    if ($testPasswordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($testPasswordPointer)
    }
    $testPassword = $null
    $configuredTestPassword = $null
    if ($localValues) {
        $localValues.Clear()
    }
}
