# Vercel Rewrite와 Oracle Nginx 운영 경계 검증
[CmdletBinding()]
param(
    [switch] $RequireRuntimeConfig
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$vercelPath = Join-Path $repoRoot 'frontend\vercel.json'
$vercelRuntimePath = Join-Path $repoRoot 'frontend\vercel.runtime.json'
$frontendEnvExamplePath = Join-Path $repoRoot 'frontend\.env.example'
$frontendRoot = Join-Path $repoRoot 'frontend'
$deploymentRunbookPath = Join-Path $repoRoot 'docs\operations\DEPLOYMENT_RUNBOOK.md'
$nginxPath = Join-Path $repoRoot 'infra\nginx\news-verification.conf.template'
$activeBackendPath = Join-Path $repoRoot 'infra\nginx\active-backend.conf.template'
$applicationConfigPath = Join-Path $repoRoot 'backend\src\main\resources\application.yml'
$prodConfigPath = Join-Path $repoRoot 'backend\src\main\resources\application-prod.yml'
$runtimePaths = @(
    'infra/nginx/active-backend.conf',
    'infra/nginx/news-verification.conf',
    'frontend/vercel.runtime.json'
)

foreach ($requiredPath in @(
        $vercelPath,
        $frontendEnvExamplePath,
        $deploymentRunbookPath,
        $nginxPath,
        $activeBackendPath
    )) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Production routing file missing: $requiredPath"
    }
}

$frontendEnvExample = Get-Content -LiteralPath $frontendEnvExamplePath -Raw
if ($frontendEnvExample -notmatch '(?m)^VITE_API_BASE_URL=/api\r?$') {
    throw 'Frontend API base path must use /api'
}
$frontendSecretVariablePattern = '(?im)^\s*VITE_[A-Z0-9_]*(?:API_KEY|APP_PASSWORD|ACCESS_TOKEN|CLIENT_SECRET|PRIVATE_KEY|SECRET|PASSWORD|TOKEN|WEBHOOK_URL)[A-Z0-9_]*\s*='
foreach ($frontendEnvironmentFile in Get-ChildItem -LiteralPath $frontendRoot -Filter '.env*' -File) {
    $frontendEnvironment = Get-Content -LiteralPath $frontendEnvironmentFile.FullName -Raw
    if ($frontendEnvironment -match $frontendSecretVariablePattern) {
        throw "Frontend environment must not declare browser-exposed Secret variables: $($frontendEnvironmentFile.Name)"
    }
}
Write-Host '[PASS] Frontend environment contains public values only'

$vercel = Get-Content -LiteralPath $vercelPath -Raw | ConvertFrom-Json
$rewrites = @($vercel.rewrites)
$expectedRewrites = [ordered]@{
    '/api/:path*' = '/api/:path*'
    '/oauth2/:path*' = '/oauth2/:path*'
    '/oauth/:path*' = '/oauth/:path*'
}

foreach ($entry in $expectedRewrites.GetEnumerator()) {
    $rewrite = @($rewrites | Where-Object { $_.source -eq $entry.Key })
    if ($rewrite.Count -ne 1) {
        throw "Vercel rewrite must exist exactly once: $($entry.Key)"
    }
    $destination = [Uri]$rewrite[0].destination
    if ($destination.Scheme -ne 'https' `
            -or $destination.Host -ne 'replace-with-backend.duckdns.org') {
        throw "Tracked Vercel rewrite must keep the DuckDNS placeholder: $($entry.Key)"
    }
    if ($destination.PathAndQuery -ne $entry.Value) {
        throw "Vercel rewrite path must be preserved: $($entry.Key)"
    }
}

$headers = @($vercel.headers)
foreach ($source in $expectedRewrites.Keys) {
    $headerRule = @($headers | Where-Object { $_.source -eq $source })
    if ($headerRule.Count -ne 1) {
        throw "Vercel cache opt-out header rule must exist exactly once: $source"
    }
    $cacheHeader = @($headerRule[0].headers | Where-Object {
            $_.key -eq 'x-vercel-enable-rewrite-caching' -and $_.value -eq '0'
        })
    if ($cacheHeader.Count -ne 1) {
        throw "Vercel external rewrite caching must be disabled: $source"
    }
}

$spaRewrite = @($rewrites | Where-Object { $_.source -eq '/(.*)' })
if ($spaRewrite.Count -ne 1 -or $spaRewrite[0].destination -ne '/index.html') {
    throw 'Vercel SPA fallback rewrite is invalid'
}
if ($rewrites[-1].source -ne '/(.*)') {
    throw 'Vercel SPA fallback must be the final rewrite'
}
Write-Host '[PASS] Vercel same-origin API and OAuth rewrites with cache opt-out'

$deploymentRunbook = Get-Content -LiteralPath $deploymentRunbookPath -Raw
if ($deploymentRunbook -notmatch '(?m)^\$deploymentUrl = vercel deploy --prod --skip-domain --local-config vercel\.runtime\.json\r?$' `
        -or $deploymentRunbook -notmatch '(?m)^vercel promote \$deploymentUrl\r?$') {
    throw 'Vercel deployment must explicitly use and promote the rendered Runtime configuration'
}
Write-Host '[PASS] Vercel deployment uses the rendered Runtime configuration'

if (Test-Path -LiteralPath $vercelRuntimePath -PathType Leaf) {
    $vercelRuntimeRaw = Get-Content -LiteralPath $vercelRuntimePath -Raw
    $vercelRuntime = $vercelRuntimeRaw | ConvertFrom-Json
    $runtimeRewrites = @($vercelRuntime.rewrites | Where-Object { $_.source -in $expectedRewrites.Keys })
    $runtimeHosts = @($runtimeRewrites | ForEach-Object { ([Uri]$_.destination).Host } | Select-Object -Unique)
    if ($runtimeRewrites.Count -ne $expectedRewrites.Count `
            -or $runtimeHosts.Count -ne 1 `
            -or $runtimeHosts[0] -eq 'replace-with-backend.duckdns.org' `
            -or -not $runtimeHosts[0].EndsWith('.duckdns.org')) {
        throw 'Rendered Vercel configuration must use one non-placeholder DuckDNS Backend host'
    }
    $expectedRuntime = (Get-Content -LiteralPath $vercelPath -Raw).Replace('replace-with-backend.duckdns.org', $runtimeHosts[0]) | ConvertFrom-Json
    if (($vercelRuntime | ConvertTo-Json -Depth 20 -Compress) `
            -ne ($expectedRuntime | ConvertTo-Json -Depth 20 -Compress)) {
        throw 'Rendered Vercel configuration must differ from the tracked template only by Backend host'
    }
    Write-Host '[PASS] Vercel Runtime configuration contains the deployment Backend host'
}
elseif ($RequireRuntimeConfig) {
    throw 'Git-excluded frontend/vercel.runtime.json is required for deployment preflight'
}
else {
    Write-Host '[NOT RUN] Vercel Runtime configuration: Git-excluded file is absent from this Clean Clone'
}

$nginx = Get-Content -LiteralPath $nginxPath -Raw
foreach ($requiredPattern in @(
        'listen 443 ssl http2;',
        'server_name __BACKEND_DUCKDNS_HOST__;',
        'include /etc/nginx/news-verification/active-backend.conf;',
        'location ^~ /actuator/',
        'location ~ ^/(api|oauth2|oauth)(/|$)',
        'proxy_set_header X-Forwarded-Proto https;',
        'proxy_pass http://news_verification_backend;'
    )) {
    if (-not $nginx.Contains($requiredPattern)) {
        throw "Nginx routing requirement missing: $requiredPattern"
    }
}
$nginxHosts = [regex]::Matches($nginx, '(?m)^\s*server_name\s+(?<host>[^;]+);')
if ($nginxHosts.Count -ne 2 `
        -or @($nginxHosts | Where-Object { $_.Groups['host'].Value -ne '__BACKEND_DUCKDNS_HOST__' }).Count -gt 0) {
    throw 'Tracked Nginx template must keep only the DuckDNS host placeholder'
}
$activeBackend = Get-Content -LiteralPath $activeBackendPath -Raw
if ($activeBackend -notmatch '(?m)^server 127\.0\.0\.1:__ACTIVE_BACKEND_PORT__ max_fails=3 fail_timeout=10s;\r?$' `
        -or ([regex]::Matches($activeBackend, '(?m)^\s*server\s+')).Count -ne 1) {
    throw 'Nginx active Backend must use a loopback Blue or Green port'
}
Write-Host '[PASS] Nginx HTTPS and Blue Green routing template'

foreach ($runtimePath in $runtimePaths) {
    & git -C $repoRoot check-ignore --quiet -- $runtimePath
    if ($LASTEXITCODE -ne 0) {
        throw "Rendered production configuration must remain excluded from Git: $runtimePath"
    }
    $trackedRuntimePath = & git -C $repoRoot ls-files --error-unmatch -- $runtimePath 2>$null
    if ($LASTEXITCODE -eq 0 -or $trackedRuntimePath) {
        throw "Rendered production configuration must not be tracked: $runtimePath"
    }
}
Write-Host '[PASS] Rendered production configuration Git exclusion'

$backendRuntimeConfigsPresent = (Test-Path -LiteralPath $applicationConfigPath -PathType Leaf) `
    -and (Test-Path -LiteralPath $prodConfigPath -PathType Leaf)
if ($backendRuntimeConfigsPresent) {
    $sensitiveConfigPattern = '(?im)^\s*(?<name>(?:password|secret|token|api-key|app-password|private-key|webhook-url|lookup-hmac-key|encryption-key|ip-hash-secret))\s*:\s*(?<value>[^#\r\n]*)'
    foreach ($backendConfigPath in @($applicationConfigPath, $prodConfigPath)) {
        $backendConfig = Get-Content -LiteralPath $backendConfigPath -Raw
        foreach ($match in [regex]::Matches($backendConfig, $sensitiveConfigPattern)) {
            $value = $match.Groups['value'].Value.Trim()
            if ($value -and -not $value.StartsWith('${')) {
                throw "Backend packaged configuration must reference an environment variable for $($match.Groups['name'].Value): $([IO.Path]::GetFileName($backendConfigPath))"
            }
        }
    }
    Write-Host '[PASS] Backend packaged configuration contains no inline Secret values'

    $prodConfig = Get-Content -LiteralPath $prodConfigPath -Raw
    if (-not $prodConfig.Contains('forward-headers-strategy: framework')) {
        throw 'Spring production Forwarded Header handling is missing'
    }
    if ($prodConfig -notmatch '(?ms)session:\s*\r?\n\s+cookie:\s*\r?\n\s+secure:\s*true') {
        throw 'Spring production Secure Session Cookie is missing'
    }
    Write-Host '[PASS] Spring production proxy and Secure Cookie configuration'
}
elseif ($RequireRuntimeConfig) {
    throw 'Git-excluded Backend application.yml and application-prod.yml are required for deployment preflight'
}
else {
    Write-Host '[NOT RUN] Backend Runtime configuration: Git-excluded files are absent from this Clean Clone'
}

if ($rewrites[0].destination -like 'https://replace-with-*') {
    Write-Host '[NOT RUN] Live DuckDNS routing: replace the deployment placeholder after the subdomain is assigned'
}
