# Local Alertmanager Secret 설정과 실행
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$environmentPath = Join-Path $repoRoot '.env'
$templatePath = Join-Path $repoRoot 'infra\alertmanager\alertmanager.yml'
$composePath = Join-Path $repoRoot 'docker-compose.yml'

if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    throw 'Ignored Local .env is required to start Alertmanager'
}

. (Join-Path $PSScriptRoot 'script-utilities.ps1')
. (Join-Path $PSScriptRoot 'alertmanager-configuration.ps1')

$values = Read-EnvironmentValues -Path $environmentPath
function Get-AlertSetting {
    param([Parameter(Mandatory)][string] $Name, [string] $Fallback = '')

    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ($value) {
        return $value
    }
    return Get-ConfiguredValue -Values $values -Name $Name -Fallback $Fallback
}

$mailUsername = Get-AlertSetting -Name 'MAIL_USERNAME'
$mailFrom = Get-AlertSetting -Name 'MAIL_FROM' -Fallback $mailUsername
$mailRecipients = Get-AlertSetting -Name 'ALERT_EMAIL_RECIPIENTS' -Fallback $mailUsername
$mailPassword = Get-AlertSetting -Name 'MAIL_APP_PASSWORD'
$slackWebhook = Get-AlertSetting -Name 'SLACK_WEBHOOK_URL'
if (-not $mailUsername -or -not $mailFrom -or -not $mailRecipients `
        -or -not $mailPassword -or -not $slackWebhook) {
    throw 'Alertmanager email identity, recipients, Gmail password and Slack Webhook are required'
}

$configuration = New-AlertmanagerRuntimeConfiguration `
    -TemplatePath $templatePath `
    -SenderEmail $mailFrom `
    -Username $mailUsername `
    -Recipients $mailRecipients
$backup = @{}
foreach ($name in @('ALERTMANAGER_CONFIG', 'MAIL_APP_PASSWORD', 'SLACK_WEBHOOK_URL')) {
    $backup[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    [Environment]::SetEnvironmentVariable('ALERTMANAGER_CONFIG', $configuration, 'Process')
    [Environment]::SetEnvironmentVariable('MAIL_APP_PASSWORD', $mailPassword, 'Process')
    [Environment]::SetEnvironmentVariable('SLACK_WEBHOOK_URL', $slackWebhook, 'Process')
    & docker compose --file $composePath --profile alerts up --detach alertmanager
    if ($LASTEXITCODE -ne 0) {
        throw "Alertmanager startup failed with exit code $LASTEXITCODE"
    }
    Write-Host '[PASS] Alertmanager started with runtime-only email and Secret configuration'
}
finally {
    foreach ($name in $backup.Keys) {
        [Environment]::SetEnvironmentVariable($name, $backup[$name], 'Process')
    }
    $configuration = $null
    $mailUsername = $null
    $mailFrom = $null
    $mailRecipients = $null
    $mailPassword = $null
    $slackWebhook = $null
    $values.Clear()
}
