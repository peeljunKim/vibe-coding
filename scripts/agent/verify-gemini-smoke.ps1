# 승인된 Gemini 외부 연결 수동 검증
param(
    [switch] $AllowExternalGemini
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$backendRoot = Join-Path $repoRoot 'backend'
$envPath = Join-Path $repoRoot '.env'
$wrapperJar = Join-Path $backendRoot '.mvn\wrapper\maven-wrapper.jar'

. (Join-Path $PSScriptRoot 'script-utilities.ps1')

if (-not $AllowExternalGemini) {
    throw 'External Gemini smoke requires -AllowExternalGemini after user approval'
}
if (-not (Test-Path -LiteralPath $envPath -PathType Leaf)) {
    throw 'Local .env file is required'
}
if (-not (Test-Path -LiteralPath $wrapperJar -PathType Leaf)) {
    throw 'Maven Wrapper JAR is required'
}

$values = Read-EnvironmentValues -Path $envPath
$apiKey = Get-ConfiguredValue -Values $values -Name 'AI_API_KEY'
if ([string]::IsNullOrWhiteSpace($apiKey)) {
    throw 'AI_API_KEY is required in the ignored Local .env file'
}

$javaPath = Resolve-Java17Path
$previousApiKey = $env:AI_API_KEY

try {
    $env:AI_API_KEY = $apiKey
    Push-Location $backendRoot
    try {
        & $javaPath `
            "-Dmaven.multiModuleProjectDirectory=$backendRoot" `
            '-classpath' $wrapperJar `
            'org.apache.maven.wrapper.MavenWrapperMain' `
            '-q' `
            '-Dtest=GeminiExternalSmokeIT' `
            'test'
        if ($LASTEXITCODE -ne 0) {
            throw "Gemini external smoke failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    $env:AI_API_KEY = $previousApiKey
}

Write-Host '[PASS] Gemini external smoke completed within the two-call limit'
