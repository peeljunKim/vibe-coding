# Native MySQL Local Schema와 애플리케이션 계정 설정
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$mysqlPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$environmentTemplatePath = Join-Path $repoRoot '.env.example'
$environmentPath = Join-Path $repoRoot '.env'

foreach ($requiredPath in @($mysqlPath, $environmentTemplatePath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required Local setup file missing: $requiredPath"
    }
}

function Invoke-MySql {
    param(
        [Parameter(Mandatory)]
        [string] $Password,

        [Parameter(Mandatory)]
        [string] $Sql,

        [Parameter(Mandatory)]
        [string] $User
    )

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $mysqlPath
    $startInfo.ArgumentList.Add('--host=127.0.0.1')
    $startInfo.ArgumentList.Add('--port=3306')
    $startInfo.ArgumentList.Add("--user=$User")
    $startInfo.ArgumentList.Add('--batch')
    $startInfo.ArgumentList.Add('--skip-column-names')
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    $startInfo.Environment['MYSQL_PWD'] = $Password

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void]$process.Start()
    $process.StandardInput.Write($Sql)
    $process.StandardInput.Close()
    $standardOutput = $process.StandardOutput.ReadToEnd()
    $standardError = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    if ($process.ExitCode -ne 0) {
        throw "MySQL command failed: $standardError"
    }

    return $standardOutput.Trim()
}

function Set-EnvironmentValue {
    param(
        [Parameter(Mandatory)]
        [string] $Content,

        [Parameter(Mandatory)]
        [string] $Name,

        [Parameter(Mandatory)]
        [string] $Value
    )

    $escapedName = [regex]::Escape($Name)
    if ($Content -match "(?m)^$escapedName=") {
        return [regex]::Replace($Content, "(?m)^$escapedName=.*$", "$Name=$Value")
    }
    return "$($Content.TrimEnd())`r`n$Name=$Value`r`n"
}

$databaseName = Read-Host 'Application Database Name'
$appUser = Read-Host 'Application MySQL User'
$secureAppPassword = Read-Host 'Application MySQL Password' -AsSecureString
$secureRootPassword = Read-Host 'MySQL Root Password' -AsSecureString

if ($databaseName -notmatch '^[A-Za-z0-9_]+$' -or $appUser -notmatch '^[A-Za-z0-9_]+$') {
    throw 'Database name and user may contain only letters, numbers, and underscores'
}

$rootPasswordPointer = [IntPtr]::Zero
$appPasswordPointer = [IntPtr]::Zero
$rootPassword = $null
$appPassword = $null

try {
    $appPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureAppPassword)
    $appPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($appPasswordPointer)
    $rootPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureRootPassword)
    $rootPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($rootPasswordPointer)
    $sqlAppPassword = $appPassword.Replace("'", "''")

    $databaseSql = @"
CREATE DATABASE IF NOT EXISTS $databaseName
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
USE $databaseName;
"@
    $accountSql = @"

CREATE USER IF NOT EXISTS '$appUser'@'localhost' IDENTIFIED BY '$sqlAppPassword';
ALTER USER '$appUser'@'localhost' IDENTIFIED BY '$sqlAppPassword';
GRANT SELECT, INSERT, UPDATE, DELETE ON $databaseName.* TO '$appUser'@'localhost';
"@
    [void](Invoke-MySql -Password $rootPassword -User 'root' -Sql ($databaseSql + $accountSql))

    $verification = Invoke-MySql -Password $appPassword -User $appUser -Sql @"
USE $databaseName;
SELECT 1;
"@
    if ($verification -ne '1') {
        throw 'Local MySQL database or application account verification failed'
    }

    $environmentContent = if (Test-Path -LiteralPath $environmentPath -PathType Leaf) {
        Get-Content -LiteralPath $environmentPath -Raw
    }
    else {
        Get-Content -LiteralPath $environmentTemplatePath -Raw
    }
    $environmentContent = Set-EnvironmentValue -Content $environmentContent -Name 'MYSQL_DATABASE' -Value $databaseName
    $environmentContent = Set-EnvironmentValue -Content $environmentContent -Name 'MYSQL_USER' -Value $appUser
    $environmentContent = Set-EnvironmentValue -Content $environmentContent -Name 'MYSQL_PASSWORD' -Value $appPassword
    $environmentContent = Set-EnvironmentValue -Content $environmentContent -Name 'DB_URL' -Value "jdbc:mysql://localhost:3306/$databaseName`?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"
    $environmentContent = Set-EnvironmentValue -Content $environmentContent -Name 'DB_USERNAME' -Value $appUser
    $environmentContent = Set-EnvironmentValue -Content $environmentContent -Name 'DB_PASSWORD' -Value $appPassword
    [IO.File]::WriteAllText($environmentPath, $environmentContent, [Text.UTF8Encoding]::new($false))

    Write-Host '[PASS] Local MySQL database and application account setup'
    Write-Host '[PASS] Application password stored only in ignored .env'
}
finally {
    if ($rootPasswordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($rootPasswordPointer)
    }
    if ($appPasswordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($appPasswordPointer)
    }
    $rootPassword = $null
    $appPassword = $null
}
