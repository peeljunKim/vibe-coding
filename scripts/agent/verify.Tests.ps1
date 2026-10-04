# Verification Harness와 공통 Script 회귀 테스트
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$verificationScript = Join-Path $PSScriptRoot 'verify.ps1'
$localMvpVerificationScript = Join-Path $PSScriptRoot 'verify-local-mvp.ps1'
$utilitiesScript = Join-Path $PSScriptRoot 'script-utilities.ps1'
$powerShell = (Get-Process -Id $PID).Path
$testRoot = Join-Path ([IO.Path]::GetTempPath()) "news-verification-tests-$([guid]::NewGuid())"
$requiredFiles = @(
    'AGENTS.md',
    '.ai/MEMORY.md',
    '.ai/RULES.md',
    '.ai/PLAN.md',
    'docs/agent/project-context.md',
    'docs/agent/workflow.md',
    'docs/agent/verification.md',
    'docs/agent/code-review.md',
    'docs/agent/backend-review.md'
)
$failures = [System.Collections.Generic.List[string]]::new()

function Invoke-Git {
    param(
        [string]$Repository,
        [string[]]$Arguments
    )

    & git -C $Repository @Arguments *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Git command failed: git $($Arguments -join ' ')"
    }
}

function New-VerificationRepository {
    $repository = Join-Path $testRoot ([guid]::NewGuid().ToString())
    $scriptDirectory = Join-Path $repository 'scripts/agent'
    New-Item -ItemType Directory -Path $scriptDirectory -Force *> $null
    Copy-Item -LiteralPath $verificationScript -Destination $scriptDirectory

    foreach ($relativePath in $requiredFiles) {
        $path = Join-Path $repository $relativePath
        New-Item -ItemType Directory -Path (Split-Path $path) -Force *> $null
        Set-Content -LiteralPath $path -Value '<!-- test fixture -->' -NoNewline
    }
    Set-Content -LiteralPath (Join-Path $repository 'README.md') -Value 'fixture' -NoNewline

    Invoke-Git $repository @('init', '--quiet')
    Invoke-Git $repository @('config', 'user.email', 'verification-tests@example.invalid')
    Invoke-Git $repository @('config', 'user.name', 'Verification Tests')
    Invoke-Git $repository @('add', '.')
    Invoke-Git $repository @('commit', '--quiet', '-m', 'test fixture')
    return $repository
}

function New-LocalMvpTestRepository {
    param([string]$FailingStep = '')

    $repository = Join-Path $testRoot ([guid]::NewGuid().ToString())
    $scriptDirectory = Join-Path $repository 'scripts\agent'
    $frontendDirectory = Join-Path $repository 'frontend'
    $toolDirectory = Join-Path $repository 'tools'
    New-Item -ItemType Directory -Path $scriptDirectory, $frontendDirectory, $toolDirectory -Force *> $null
    Copy-Item -LiteralPath $localMvpVerificationScript -Destination $scriptDirectory

    $steps = @(
        @{ File = 'verify.ps1'; Name = 'base' },
        @{ File = 'verify-publisher-native-mysql.ps1'; Name = 'mysql' },
        @{ File = 'verify-redis-analysis-job.ps1'; Name = 'redis' }
    )
    foreach ($step in $steps) {
        $exitCode = if ($FailingStep -eq $step.Name) { 1 } else { 0 }
        $content = @"
Add-Content -LiteralPath `$env:LOCAL_MVP_TEST_LOG -Value '$($step.Name)'
exit $exitCode
"@
        Set-Content -LiteralPath (Join-Path $scriptDirectory $step.File) -Value $content
    }

    $npmExitCode = if ($FailingStep -eq 'e2e') { 1 } else { 0 }
    if ($IsWindows) {
        $npmPath = Join-Path $toolDirectory 'npm.cmd'
        $npmCommand = @"
@if "%1"=="--version" @exit /b 0
@echo e2e>> "%LOCAL_MVP_TEST_LOG%"
@exit /b $npmExitCode
"@
        Set-Content -LiteralPath $npmPath -Value $npmCommand
    }
    else {
        $npmPath = Join-Path $toolDirectory 'npm'
        $npmCommand = @"
#!/bin/sh
if [ "`$1" = "--version" ]; then exit 0; fi
echo e2e >> "`$LOCAL_MVP_TEST_LOG"
exit $npmExitCode
"@
        Set-Content -LiteralPath $npmPath -Value $npmCommand -NoNewline
        & chmod +x $npmPath
    }

    return [pscustomobject]@{
        Repository = $repository
        LogPath = Join-Path $repository 'steps.log'
        ToolDirectory = $toolDirectory
    }
}

function Invoke-Verification {
    param([string]$Repository)

    $script = Join-Path $Repository 'scripts/agent/verify.ps1'
    $output = & $powerShell -NoProfile -File $script -Scope docs 2>&1 | Out-String
    return @{
        ExitCode = $LASTEXITCODE
        Output = $output
    }
}

function Invoke-LocalMvpVerification {
    param([pscustomobject]$Fixture)

    $previousPath = $env:PATH
    $previousLog = $env:LOCAL_MVP_TEST_LOG
    try {
        $env:PATH = "$($Fixture.ToolDirectory)$([IO.Path]::PathSeparator)$previousPath"
        $env:LOCAL_MVP_TEST_LOG = $Fixture.LogPath
        $output = & $powerShell -NoProfile -File (Join-Path $Fixture.Repository 'scripts\agent\verify-local-mvp.ps1') 2>&1 | Out-String
        return [pscustomobject]@{
            ExitCode = $LASTEXITCODE
            Output = $output
        }
    }
    finally {
        $env:PATH = $previousPath
        $env:LOCAL_MVP_TEST_LOG = $previousLog
    }
}

function Assert-Equal {
    param(
        [string]$Name,
        [object]$Expected,
        [object]$Actual
    )

    if ($Expected -ne $Actual) {
        $failures.Add("$Name`: expected '$Expected', got '$Actual'")
    }
}

function Assert-Contains {
    param(
        [string]$Name,
        [string]$Actual,
        [string]$ExpectedText
    )

    if (-not $Actual.Contains($ExpectedText)) {
        $failures.Add("$Name`: output did not contain '$ExpectedText'`n$Actual")
    }
}

try {
    New-Item -ItemType Directory -Path $testRoot -Force *> $null
    . $utilitiesScript
    $environmentFixture = Join-Path $testRoot 'environment.fixture'
    Set-Content -LiteralPath $environmentFixture -Value @(
        'MYSQL_DATABASE=news_verification'
        'MYSQL_USER=replace-with-user'
        '# ignored comment'
    )
    $environmentValues = Read-EnvironmentValues -Path $environmentFixture
    Assert-Equal 'environment value parsing' 'news_verification' $environmentValues['MYSQL_DATABASE']
    Assert-Equal 'placeholder fallback' 'fallback_user' (Get-ConfiguredValue -Values $environmentValues -Name 'MYSQL_USER' -Fallback 'fallback_user')
    Assert-Equal 'environment value replacement' "MYSQL_DATABASE=updated" (Set-EnvironmentValue -Content 'MYSQL_DATABASE=old' -Name 'MYSQL_DATABASE' -Value 'updated')

    $repository = New-VerificationRepository
    $result = Invoke-Verification $repository
    Assert-Equal 'clean repository exits successfully' 0 $result.ExitCode
    Assert-Contains 'clean repository checks required documents' $result.Output '[PASS] Harness file structure'
    Assert-Contains 'clean repository checks staged whitespace' $result.Output '[PASS] Git staged diff whitespace check'

    foreach ($reviewDocument in @('docs/agent/code-review.md', 'docs/agent/backend-review.md')) {
        $repository = New-VerificationRepository
        Remove-Item -LiteralPath (Join-Path $repository $reviewDocument)
        $result = Invoke-Verification $repository
        Assert-Equal "$reviewDocument is required" 1 $result.ExitCode
        Assert-Contains "$reviewDocument failure identifies the missing file" $result.Output "Required Harness file missing: $($reviewDocument.Replace('/', '\'))"
    }

    $repository = New-VerificationRepository
    Add-Content -LiteralPath (Join-Path $repository 'README.md') -Value "`nunstaged trailing whitespace " -NoNewline
    $result = Invoke-Verification $repository
    Assert-Equal 'unstaged whitespace exits with failure' 1 $result.ExitCode
    Assert-Contains 'unstaged whitespace fails the existing check' $result.Output 'Git diff whitespace check failed with exit code'

    $repository = New-VerificationRepository
    Add-Content -LiteralPath (Join-Path $repository 'README.md') -Value "`nstaged trailing whitespace " -NoNewline
    Invoke-Git $repository @('add', 'README.md')
    $result = Invoke-Verification $repository
    Assert-Equal 'staged-only whitespace exits with failure' 1 $result.ExitCode
    Assert-Contains 'staged-only whitespace reaches the staged check' $result.Output '[PASS] Git diff whitespace check'
    Assert-Contains 'staged-only whitespace fails the staged check' $result.Output 'Git staged diff whitespace check failed with exit code'

    $fixture = New-LocalMvpTestRepository
    $result = Invoke-LocalMvpVerification -Fixture $fixture
    Assert-Equal 'successful orchestration exit code' 0 $result.ExitCode
    if (-not (Test-Path -LiteralPath $fixture.LogPath)) {
        throw "Successful orchestration log missing`n$($result.Output)"
    }
    Assert-Equal 'successful orchestration order' 'base,mysql,redis,e2e' ((Get-Content -LiteralPath $fixture.LogPath) -join ',')

    $fixture = New-LocalMvpTestRepository -FailingStep 'mysql'
    $result = Invoke-LocalMvpVerification -Fixture $fixture
    Assert-Equal 'failed orchestration exit code' 1 $result.ExitCode
    if (-not (Test-Path -LiteralPath $fixture.LogPath)) {
        throw "Failed orchestration log missing`n$($result.Output)"
    }
    Assert-Equal 'failed orchestration stops immediately' 'base,mysql' ((Get-Content -LiteralPath $fixture.LogPath) -join ',')
}
finally {
    if (Test-Path -LiteralPath $testRoot) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host '[PASS] Verification Harness and Local MVP orchestration regression tests'
