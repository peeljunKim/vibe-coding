# Verification Harness의 Git·문서 검사 회귀 테스트
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$verificationScript = Join-Path $PSScriptRoot 'verify.ps1'
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

function Invoke-Verification {
    param([string]$Repository)

    $script = Join-Path $Repository 'scripts/agent/verify.ps1'
    $output = & $powerShell -NoProfile -File $script -Scope docs 2>&1 | Out-String
    return @{
        ExitCode = $LASTEXITCODE
        Output = $output
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

Write-Host '[PASS] Verification Harness regression tests'
