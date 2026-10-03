# Local MVP 통합 회귀 진입점 테스트
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$verificationScript = Join-Path $PSScriptRoot 'verify-local-mvp.ps1'
$powerShell = (Get-Process -Id $PID).Path
$testRoot = Join-Path ([IO.Path]::GetTempPath()) "news-verification-local-mvp-tests-$([guid]::NewGuid())"
$failures = [System.Collections.Generic.List[string]]::new()

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

function New-TestRepository {
    param([string]$FailingStep = '')

    $repository = Join-Path $testRoot ([guid]::NewGuid().ToString())
    $scriptDirectory = Join-Path $repository 'scripts\agent'
    $frontendDirectory = Join-Path $repository 'frontend'
    $toolDirectory = Join-Path $repository 'tools'
    New-Item -ItemType Directory -Path $scriptDirectory, $frontendDirectory, $toolDirectory -Force *> $null
    Copy-Item -LiteralPath $verificationScript -Destination $scriptDirectory

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
    $npmCommand = @"
@if "%1"=="--version" @exit /b 0
@echo e2e>> "%LOCAL_MVP_TEST_LOG%"
@exit /b $npmExitCode
"@
    Set-Content -LiteralPath (Join-Path $toolDirectory 'npm.cmd') -Value $npmCommand

    return [pscustomobject]@{
        Repository = $repository
        LogPath = Join-Path $repository 'steps.log'
        ToolDirectory = $toolDirectory
    }
}

function Invoke-LocalMvpVerification {
    param([pscustomobject]$Fixture)

    $previousPath = $env:PATH
    $previousLog = $env:LOCAL_MVP_TEST_LOG
    try {
        $env:PATH = "$($Fixture.ToolDirectory);$previousPath"
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

try {
    $fixture = New-TestRepository
    $result = Invoke-LocalMvpVerification -Fixture $fixture
    Assert-Equal 'successful orchestration exit code' 0 $result.ExitCode
    if (-not (Test-Path -LiteralPath $fixture.LogPath)) {
        throw "Successful orchestration log missing`n$($result.Output)"
    }
    Assert-Equal 'successful orchestration order' "base`r`nmysql`r`nredis`r`ne2e" ((Get-Content -LiteralPath $fixture.LogPath) -join "`r`n")

    $fixture = New-TestRepository -FailingStep 'mysql'
    $result = Invoke-LocalMvpVerification -Fixture $fixture
    Assert-Equal 'failed orchestration exit code' 1 $result.ExitCode
    if (-not (Test-Path -LiteralPath $fixture.LogPath)) {
        throw "Failed orchestration log missing`n$($result.Output)"
    }
    Assert-Equal 'failed orchestration stops immediately' "base`r`nmysql" ((Get-Content -LiteralPath $fixture.LogPath) -join "`r`n")
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

Write-Host '[PASS] Local MVP verification Harness regression tests'
