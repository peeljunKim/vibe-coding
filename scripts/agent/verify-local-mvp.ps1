# Local MVP 전체 회귀 검증 진입점
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$frontendRoot = Join-Path $repoRoot 'frontend'
$powerShell = (Get-Process -Id $PID).Path

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory)]
        [string]$Name,

        [Parameter(Mandatory)]
        [string]$WorkingDirectory,

        [Parameter(Mandatory)]
        [string]$Command,

        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    Write-Host "[RUN] $Name"
    Push-Location $WorkingDirectory
    try {
        & $Command @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$Name failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
    Write-Host "[PASS] $Name"
}

function Resolve-NpmCommand {
    $candidates = @(
        @(Get-Command npm.cmd -All -ErrorAction SilentlyContinue)
        @(Get-Command npm -All -ErrorAction SilentlyContinue)
    )
    foreach ($candidate in $candidates) {
        & $candidate.Source --version *> $null
        if ($LASTEXITCODE -eq 0) {
            return $candidate.Source
        }
    }
    throw '[Frontend Browser E2E 실행을 위한 정상 npm 명령이 필요합니다.]'
}

$verificationSteps = @(
    @{ Name = 'Repository full verification'; Script = 'verify.ps1'; Arguments = @('-Scope', 'all') },
    @{ Name = 'Native MySQL repository integration'; Script = 'verify-publisher-native-mysql.ps1'; Arguments = @() },
    @{ Name = 'Docker Redis integration'; Script = 'verify-redis-analysis-job.ps1'; Arguments = @() }
)

foreach ($step in $verificationSteps) {
    $arguments = @('-NoProfile', '-File', (Join-Path $PSScriptRoot $step.Script)) + $step.Arguments
    Invoke-CheckedCommand `
        -Name $step.Name `
        -WorkingDirectory $repoRoot `
        -Command $powerShell `
        -Arguments $arguments
}

$npmCommand = Resolve-NpmCommand

Invoke-CheckedCommand `
    -Name 'Frontend Playwright E2E' `
    -WorkingDirectory $frontendRoot `
    -Command $npmCommand `
    -Arguments @('run', 'e2e')

Write-Host '[PASS] Local MVP integrated regression verification'
