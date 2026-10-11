# Alertmanager 실행 설정 생성

function New-AlertmanagerRuntimeConfiguration {
    param(
        [Parameter(Mandatory)][string] $TemplatePath,
        [Parameter(Mandatory)][string] $SenderEmail,
        [Parameter(Mandatory)][string] $Username,
        [Parameter(Mandatory)][string] $Recipients
    )

    $normalizedRecipients = [Collections.Generic.List[string]]::new()
    foreach ($value in $Recipients.Split(',')) {
        $address = $value.Trim()
        if (-not $address) {
            continue
        }
        try {
            $parsed = [Net.Mail.MailAddress]::new($address)
        }
        catch {
            throw 'Alert email recipients contain an invalid address'
        }
        if (-not $parsed.Address.Equals($address, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Alert email recipients must contain addresses only'
        }
        $normalizedRecipients.Add($address)
    }
    if ($normalizedRecipients.Count -eq 0) {
        throw 'At least one alert email recipient is required'
    }

    foreach ($address in @($SenderEmail, $Username)) {
        try {
            $parsed = [Net.Mail.MailAddress]::new($address)
        }
        catch {
            throw 'Alert SMTP identity contains an invalid address'
        }
        if (-not $parsed.Address.Equals($address, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Alert SMTP identity must contain an address only'
        }
    }

    $quote = {
        param([string] $Value)
        $escaped = $Value.Replace('\', '\\').Replace('"', '\"')
        return '"' + $escaped + '"'
    }
    $template = Get-Content -Raw -LiteralPath $TemplatePath
    $configuration = $template.Replace('__ALERT_SMTP_FROM__', (& $quote $SenderEmail))
    $configuration = $configuration.Replace('__ALERT_SMTP_USERNAME__', (& $quote $Username))
    $configuration = $configuration.Replace(
        '__ALERT_EMAIL_RECIPIENTS__',
        (& $quote ($normalizedRecipients -join ','))
    )
    return $configuration
}
