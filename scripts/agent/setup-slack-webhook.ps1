# Local Slack Webhook Secret 저장
[CmdletBinding()]
param(
    [switch] $ReceiveFromBrowser
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$environmentPath = Join-Path $repoRoot '.env'
$utilitiesPath = Join-Path $PSScriptRoot 'script-utilities.ps1'
$webhookUrl = $null
$environmentContent = $null
$updatedContent = $null
$listener = $null
$requestBody = $null
$encodedValue = $null
$requestToken = $null

if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    throw 'Ignored Local .env is required before storing the Slack Webhook URL'
}

& git -C $repoRoot check-ignore --quiet -- .env
if ($LASTEXITCODE -ne 0) {
    throw 'Local .env must remain excluded from Git'
}

. $utilitiesPath

function Save-SlackWebhookUrl {
    param([Parameter(Mandatory)][string] $Value)

    if ($Value -notmatch '^https://hooks\.slack\.com/services/[A-Za-z0-9]+/[A-Za-z0-9]+/[A-Za-z0-9]+$') {
        throw 'Input does not contain a valid Slack Incoming Webhook URL'
    }

    $script:environmentContent = Get-Content -Raw -LiteralPath $environmentPath
    $script:updatedContent = Set-EnvironmentValue `
        -Content $script:environmentContent `
        -Name 'SLACK_WEBHOOK_URL' `
        -Value $Value
    [IO.File]::WriteAllText(
        $environmentPath,
        $script:updatedContent,
        [Text.UTF8Encoding]::new($false)
    )
}

try {
    if ($ReceiveFromBrowser) {
        $requestToken = [Convert]::ToHexString(
            [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
        )
        $listener = [Net.HttpListener]::new()
        $listener.Prefixes.Add('http://127.0.0.1:43789/')
        $listener.Start()
        Write-Host '[WAIT] Open http://127.0.0.1:43789/ to provide the Slack Webhook URL'

        $getContext = $listener.GetContext()
        $getContext.Response.Headers.Add('Cache-Control', 'no-store')
        $form = @"
<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>Slack Webhook 설정</title></head>
<body><main><h1>Slack Webhook 설정</h1><form method="post"><input name="requestToken" type="hidden" value="$requestToken"><label>Webhook URL <input name="webhook" type="password" required></label><button type="submit">저장</button></form></main></body></html>
"@
        $formBytes = [Text.Encoding]::UTF8.GetBytes($form)
        $getContext.Response.ContentType = 'text/html; charset=utf-8'
        $getContext.Response.ContentLength64 = $formBytes.Length
        $getContext.Response.OutputStream.Write($formBytes, 0, $formBytes.Length)
        $getContext.Response.Close()

        $postContext = $null
        for ($requestCount = 0; $requestCount -lt 3 -and -not $postContext; $requestCount++) {
            $candidateContext = $listener.GetContext()
            if ($candidateContext.Request.HttpMethod -ne 'POST') {
                $candidateContext.Response.StatusCode = 204
                $candidateContext.Response.Close()
                continue
            }
            if ($candidateContext.Request.ContentLength64 -lt 0 `
                    -or $candidateContext.Request.ContentLength64 -gt 2048) {
                $candidateContext.Response.StatusCode = 413
                $candidateContext.Response.Close()
                continue
            }
            $reader = [IO.StreamReader]::new(
                $candidateContext.Request.InputStream,
                $candidateContext.Request.ContentEncoding
            )
            $candidateBody = $reader.ReadToEnd()
            $reader.Dispose()
            $encodedToken = ($candidateBody -split '&' `
                | Where-Object { $_.StartsWith('requestToken=') } `
                | Select-Object -First 1)
            $submittedToken = if ($encodedToken) {
                [Uri]::UnescapeDataString(
                    $encodedToken.Substring('requestToken='.Length).Replace('+', ' ')
                )
            }
            else {
                ''
            }
            $expectedTokenBytes = [Text.Encoding]::UTF8.GetBytes($requestToken)
            $submittedTokenBytes = [Text.Encoding]::UTF8.GetBytes($submittedToken)
            $validToken = $expectedTokenBytes.Length -eq $submittedTokenBytes.Length `
                -and [Security.Cryptography.CryptographicOperations]::FixedTimeEquals(
                    $expectedTokenBytes,
                    $submittedTokenBytes
                )
            if (-not $validToken) {
                $candidateContext.Response.StatusCode = 403
                $candidateContext.Response.Close()
                continue
            }
            $requestBody = $candidateBody
            $postContext = $candidateContext
        }
        if (-not $postContext) {
            throw 'Invalid Local Slack Webhook setup request'
        }
        if ([string]::IsNullOrWhiteSpace($requestBody) -or $requestBody.Length -gt 2048) {
            throw 'Invalid Local Slack Webhook setup request'
        }
        $encodedValue = ($requestBody -split '&' | Where-Object { $_.StartsWith('webhook=') } | Select-Object -First 1)
        if (-not $encodedValue) {
            throw 'Slack Webhook setup request value is missing'
        }
        $webhookUrl = [Uri]::UnescapeDataString(
            $encodedValue.Substring('webhook='.Length).Replace('+', ' ')
        )
        Save-SlackWebhookUrl -Value $webhookUrl

        $resultBytes = [Text.Encoding]::UTF8.GetBytes('Slack Webhook 설정 완료')
        $postContext.Response.Headers.Add('Cache-Control', 'no-store')
        $postContext.Response.ContentType = 'text/plain; charset=utf-8'
        $postContext.Response.ContentLength64 = $resultBytes.Length
        $postContext.Response.OutputStream.Write($resultBytes, 0, $resultBytes.Length)
        $postContext.Response.Close()
    }
    else {
        $webhookUrl = Get-Clipboard -Raw
        Save-SlackWebhookUrl -Value $webhookUrl
    }
    Write-Host '[PASS] Slack Webhook URL stored only in ignored .env'
}
finally {
    if ($listener) {
        try {
            if ($listener.IsListening) {
                $listener.Stop()
            }
            $listener.Close()
        }
        catch [ObjectDisposedException] {
            # 이미 종료된 Local 수신기
        }
    }
    Set-Clipboard -Value ''
    $webhookUrl = $null
    $requestBody = $null
    $encodedValue = $null
    $requestToken = $null
    $environmentContent = $null
    $updatedContent = $null
}

