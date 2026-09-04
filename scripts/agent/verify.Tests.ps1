# Harness 필수 문서 검증 회귀 테스트
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$verifyScript = Join-Path $PSScriptRoot 'verify.ps1'
$requiredHarnessFiles = @(
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
$tokens = $null
$parseErrors = $null
$verifyAst = [System.Management.Automation.Language.Parser]::ParseFile(
    $verifyScript,
    [ref]$tokens,
    [ref]$parseErrors
)
if ($parseErrors.Count -gt 0) {
    throw "verify.ps1 parsing failed: $($parseErrors[0].Message)"
}
$docsVerificationFunction = $verifyAst.Find({
        param($node)
        $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
        $node.Name -eq 'Invoke-DocsVerification'
    }, $true)
if ($null -eq $docsVerificationFunction) {
    throw 'Invoke-DocsVerification function not found'
}
Invoke-Expression $docsVerificationFunction.Extent.Text

function New-VerificationFixture {
    param([string]$Name)

    $fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) "vibe-coding-$Name-$([guid]::NewGuid())"
    foreach ($relativePath in $requiredHarnessFiles) {
        $path = Join-Path $fixtureRoot $relativePath
        New-Item -ItemType Directory -Path (Split-Path $path -Parent) -Force | Out-Null
        Set-Content -LiteralPath $path -Value 'test fixture'
    }

    return $fixtureRoot
}

function Invoke-DocsVerificationFixture {
    param([string]$FixtureRoot)

    $script:repoRoot = $FixtureRoot
    try {
        $output = Invoke-DocsVerification *>&1 | Out-String
        return @{
            Succeeded = $true
            Output = $output
            Error = ''
        }
    }
    catch {
        return @{
            Succeeded = $false
            Output = ''
            Error = $_.Exception.Message
        }
    }
}

function Assert-Equal {
    param(
        [object]$Expected,
        [object]$Actual,
        [string]$Because
    )

    if ($Actual -ne $Expected) {
        throw "$Because (expected: $Expected, actual: $Actual)"
    }
}

function Assert-Matches {
    param(
        [string]$Pattern,
        [string]$Actual,
        [string]$Because
    )

    if ($Actual -notmatch $Pattern) {
        throw "$Because`nOutput:`n$Actual"
    }
}

$tests = @(
    @{
        Name = '모든 Harness 문서가 있으면 docs 검증에 성공한다'
        Run = {
            $fixtureRoot = New-VerificationFixture 'complete'
            try {
                $result = Invoke-DocsVerificationFixture $fixtureRoot

                Assert-Equal $true $result.Succeeded "Complete Harness should pass docs verification`nError:`n$($result.Error)"
                Assert-Matches '\[PASS\] Harness file structure' $result.Output 'Harness structure success should be reported'
            }
            finally {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    },
    @{
        Name = '공통 코드 리뷰 문서가 없으면 해당 경로와 함께 실패한다'
        Run = {
            $fixtureRoot = New-VerificationFixture 'missing-code-review'
            try {
                Remove-Item -LiteralPath (Join-Path $fixtureRoot 'docs/agent/code-review.md')
                $result = Invoke-DocsVerificationFixture $fixtureRoot

                Assert-Equal $false $result.Succeeded 'Missing code review guidance should fail docs verification'
                Assert-Matches 'Required Harness file missing: docs[\\/]agent[\\/]code-review\.md' $result.Error 'Failure should identify the missing code review guidance'
            }
            finally {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    },
    @{
        Name = 'Backend 코드 리뷰 문서가 없으면 해당 경로와 함께 실패한다'
        Run = {
            $fixtureRoot = New-VerificationFixture 'missing-backend-review'
            try {
                Remove-Item -LiteralPath (Join-Path $fixtureRoot 'docs/agent/backend-review.md')
                $result = Invoke-DocsVerificationFixture $fixtureRoot

                Assert-Equal $false $result.Succeeded 'Missing backend review guidance should fail docs verification'
                Assert-Matches 'Required Harness file missing: docs[\\/]agent[\\/]backend-review\.md' $result.Error 'Failure should identify the missing backend review guidance'
            }
            finally {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }
)

$failures = 0
foreach ($test in $tests) {
    try {
        & $test.Run
        Write-Host "[PASS] $($test.Name)"
    }
    catch {
        $failures++
        Write-Error "[FAIL] $($test.Name): $($_.Exception.Message)" -ErrorAction Continue
    }
}

if ($failures -gt 0) {
    throw "$failures verification test(s) failed"
}

Write-Host "[PASS] $($tests.Count) verification tests"
