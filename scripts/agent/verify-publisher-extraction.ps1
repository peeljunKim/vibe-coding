# 초기 언론사 실제 기사 추출 시험 실행
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateRange(1, [int]::MaxValue)]
    [int] $TimeoutSeconds,

    [Parameter(Mandatory)]
    [ValidateRange(1, [int]::MaxValue)]
    [int] $MaxResponseBytes,

    [Parameter(Mandatory)]
    [ValidateRange(0, [int]::MaxValue)]
    [int] $MaxRedirects,

    [string] $InputPath,

    [string] $ReportPath
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$backendRoot = Join-Path $repoRoot 'backend'
$javaPath = 'C:\Program Files\Java\jdk-17\bin\java.exe'
$wrapperJar = Join-Path $backendRoot '.mvn\wrapper\maven-wrapper.jar'

if (-not $InputPath) {
    $InputPath = Join-Path $repoRoot 'output\publisher-extraction-input.tsv'
}
if (-not $ReportPath) {
    $ReportPath = Join-Path $repoRoot 'output\publisher-extraction-report.tsv'
}

foreach ($requiredPath in @($InputPath, $javaPath, $wrapperJar)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required extraction test file missing: $requiredPath"
    }
}

$resolvedInputPath = (Resolve-Path -LiteralPath $InputPath).Path
$resolvedReportPath = [IO.Path]::GetFullPath($ReportPath)

Push-Location $backendRoot
try {
    & $javaPath "-Dmaven.multiModuleProjectDirectory=$backendRoot" '-classpath' $wrapperJar `
        'org.apache.maven.wrapper.MavenWrapperMain' '-q' `
        '-Dtest=PublisherArticleExtractionSmokeIT' `
        "-Darticle.smoke.input=$resolvedInputPath" `
        "-Darticle.smoke.report=$resolvedReportPath" `
        "-Darticle.smoke.timeoutSeconds=$TimeoutSeconds" `
        "-Darticle.smoke.maxResponseBytes=$MaxResponseBytes" `
        "-Darticle.smoke.maxRedirects=$MaxRedirects" `
        'test'
    if ($LASTEXITCODE -ne 0) {
        throw "Publisher extraction smoke test failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

$results = Import-Csv -LiteralPath $resolvedReportPath -Delimiter "`t"
$successCount = @($results | Where-Object { -not $_.errorCode }).Count
$failureCount = @($results | Where-Object { $_.errorCode }).Count

Write-Host "[PASS] Publisher extraction smoke process: $($results.Count)"
Write-Host "[INFO] Extraction success: $successCount"
Write-Host "[INFO] Extraction failure: $failureCount"
Write-Host "[INFO] Local report: $resolvedReportPath"
