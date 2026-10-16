# 운영 Routing Template 값 검증과 메모리 치환
Set-StrictMode -Version Latest

function New-ProductionRoutingConfiguration {
    param(
        [Parameter(Mandatory)][string] $RepositoryRoot,
        [Parameter(Mandatory)][string] $BackendHost,
        [Parameter(Mandatory)][string] $FrontendOrigin,
        [Parameter(Mandatory)][string] $OriginSecret,
        [ValidateSet(8081, 8082)][int] $ActivePort = 8081
    )

    if ($BackendHost -cnotmatch '^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.duckdns\.org$' `
            -or $BackendHost -eq 'replace-with-backend.duckdns.org') {
        throw 'A non-placeholder DuckDNS host is required'
    }
    $publicOrigin = $null
    if (-not [Uri]::TryCreate($FrontendOrigin, [UriKind]::Absolute, [ref]$publicOrigin) `
            -or $publicOrigin.Scheme -ne 'https' `
            -or -not $publicOrigin.IsDefaultPort `
            -or $publicOrigin.UserInfo -or $publicOrigin.Query -or $publicOrigin.Fragment `
            -or $publicOrigin.AbsolutePath -ne '/' `
            -or $publicOrigin.Host -notmatch '^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\.[a-z]{2,}$' `
            -or $publicOrigin.Host -eq $BackendHost) {
        throw 'A distinct HTTPS Frontend origin without path, credentials or custom port is required'
    }
    if ($OriginSecret -cnotmatch '^[a-f0-9]{64}$' -or $OriginSecret -match '^(.)\1{63}$') {
        throw 'Origin Secret must be a separately prepared 256-bit lowercase hexadecimal value'
    }

    $nginx = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'infra/nginx/news-verification.conf.template') -Raw
    $nginx = $nginx.Replace('__BACKEND_DUCKDNS_HOST__', $BackendHost)
    $nginx = $nginx.Replace('__FRONTEND_PUBLIC_HOST__', $publicOrigin.Host)
    $nginx = $nginx.Replace('__ORIGIN_PROXY_SECRET__', $OriginSecret)
    $active = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'infra/nginx/active-backend.conf.template') -Raw
    $active = $active.Replace('__ACTIVE_BACKEND_PORT__', [string]$ActivePort)
    $vercel = Get-Content -LiteralPath (Join-Path $RepositoryRoot 'frontend/vercel.json') -Raw
    $vercel = $vercel.Replace('replace-with-backend.duckdns.org', $BackendHost)

    return @{
        'infra/nginx/news-verification.conf' = $nginx
        'infra/nginx/active-backend.conf' = $active
        'frontend/vercel.runtime.json' = $vercel
    }
}
