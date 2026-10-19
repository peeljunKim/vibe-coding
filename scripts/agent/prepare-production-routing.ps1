# Process 환경 변수 기반 Git 제외 운영 Routing 파일 준비
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'production-routing-configuration.ps1')
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path

$requiredNames = @('BACKEND_DUCKDNS_HOST', 'FRONTEND_PUBLIC_ORIGIN', 'ORIGIN_PROXY_SECRET')
foreach ($name in $requiredNames) {
    if (-not [Environment]::GetEnvironmentVariable($name, 'Process')) {
        throw "Required Process environment variable missing: $name"
    }
}
$activePort = if ($env:ACTIVE_BACKEND_PORT) { [int]$env:ACTIVE_BACKEND_PORT } else { 8081 }
$configuration = New-ProductionRoutingConfiguration -RepositoryRoot $repositoryRoot `
    -BackendHost $env:BACKEND_DUCKDNS_HOST -FrontendOrigin $env:FRONTEND_PUBLIC_ORIGIN `
    -OriginSecret $env:ORIGIN_PROXY_SECRET -ActivePort $activePort

foreach ($relativePath in $configuration.Keys) {
    & git -C $repositoryRoot check-ignore --quiet -- $relativePath
    if ($LASTEXITCODE -ne 0) { throw 'Runtime configuration must be Git ignored' }
    $tracked = & git -C $repositoryRoot ls-files -- $relativePath
    if ($tracked) { throw 'Runtime configuration must not be tracked' }
    if (Test-Path -LiteralPath (Join-Path $repositoryRoot $relativePath)) {
        throw 'Existing Runtime configuration must be backed up and moved before preparation'
    }
}
foreach ($relativePath in $configuration.Keys) {
    $target = Join-Path $repositoryRoot $relativePath
    $newFile = [IO.File]::Open($target, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    $newFile.Dispose()
    if ($IsWindows) {
        $currentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
        & icacls $target /inheritance:r /grant:r "${currentIdentity}:(F)" *> $null
    }
    else {
        & chmod 600 $target
    }
    if ($LASTEXITCODE -ne 0) { throw 'Runtime file permission restriction failed' }
    [IO.File]::WriteAllText($target, $configuration[$relativePath], [Text.UTF8Encoding]::new($false))
}
Write-Host '[PASS] Git-excluded production routing files prepared with restricted permissions'
Write-Host '[NOT RUN] Vercel Middleware connection and real deployment require separate verification'
