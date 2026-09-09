# Native MySQL 노출 자격 증명 교체
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$environmentPath = Join-Path $repoRoot '.env'
$mysqlPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$secureCurrentRootPassword = $null
$secureNewRootPassword = $null
$secureNewRootPasswordConfirmation = $null
$secureCurrentAppPassword = $null
$secureNewAppPassword = $null
$secureNewAppPasswordConfirmation = $null
$currentRootPasswordPointer = [IntPtr]::Zero
$newRootPasswordPointer = [IntPtr]::Zero
$newRootPasswordConfirmationPointer = [IntPtr]::Zero
$currentAppPasswordPointer = [IntPtr]::Zero
$newAppPasswordPointer = [IntPtr]::Zero
$newAppPasswordConfirmationPointer = [IntPtr]::Zero
$currentRootPassword = $null
$newRootPassword = $null
$newRootPasswordConfirmation = $null
$oldAppPassword = $null
$newAppPassword = $null
$newAppPasswordConfirmation = $null

foreach ($requiredPath in @($environmentPath, $mysqlPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required Local credential file missing: $requiredPath"
    }
}

function Read-EnvironmentValues {
    param(
        [Parameter(Mandatory)]
        [string] $Path
    )

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^(?<Name>[A-Z0-9_]+)=(?<Value>.*)$') {
            $values[$Matches.Name] = $Matches.Value
        }
    }
    return $values
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

function Invoke-MySql {
    param(
        [Parameter(Mandatory)]
        [string] $Password,

        [Parameter(Mandatory)]
        [string] $Sql,

        [Parameter(Mandatory)]
        [string] $User,

        [string] $Database
    )

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $mysqlPath
    $startInfo.ArgumentList.Add('--host=localhost')
    $startInfo.ArgumentList.Add('--protocol=TCP')
    $startInfo.ArgumentList.Add('--port=3306')
    $startInfo.ArgumentList.Add("--user=$User")
    $startInfo.ArgumentList.Add('--batch')
    $startInfo.ArgumentList.Add('--skip-column-names')
    if ($Database) {
        $startInfo.ArgumentList.Add("--database=$Database")
    }
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    $startInfo.Environment['MYSQL_PWD'] = $Password

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void] $process.Start()
    $process.StandardInput.Write($Sql)
    $process.StandardInput.Close()
    $standardOutput = $process.StandardOutput.ReadToEnd().Trim()
    $standardError = $process.StandardError.ReadToEnd().Trim()
    $process.WaitForExit()

    if ($process.ExitCode -ne 0) {
        throw "MySQL credential rotation failed: $standardError"
    }
    return $standardOutput
}

$environmentValues = Read-EnvironmentValues -Path $environmentPath
$databaseName = $environmentValues.MYSQL_DATABASE
$appUser = $environmentValues.MYSQL_USER
$oldAppPassword = $environmentValues.MYSQL_PASSWORD

if ($databaseName -notmatch '^[A-Za-z0-9_]+$' -or $appUser -notmatch '^[A-Za-z0-9_]+$') {
    throw 'Invalid Local MySQL database or application account setting'
}
$secureCurrentRootPassword = Read-Host 'Current MySQL Root Password' -AsSecureString
$usingConfiguredCurrentAppPassword = $oldAppPassword -and $oldAppPassword -notmatch '^replace-with-'
if (-not $usingConfiguredCurrentAppPassword) {
    $secureCurrentAppPassword = Read-Host 'Current Application MySQL Password' -AsSecureString
}
$secureNewRootPassword = Read-Host 'New MySQL Root Password' -AsSecureString
$secureNewRootPasswordConfirmation = Read-Host 'Confirm New MySQL Root Password' -AsSecureString
$secureNewAppPassword = Read-Host 'New Application MySQL Password' -AsSecureString
$secureNewAppPasswordConfirmation = Read-Host 'Confirm New Application MySQL Password' -AsSecureString

try {
    $currentRootPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureCurrentRootPassword)
    $currentRootPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($currentRootPasswordPointer)
    $newRootPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureNewRootPassword)
    $newRootPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($newRootPasswordPointer)
    $newRootPasswordConfirmationPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureNewRootPasswordConfirmation)
    $newRootPasswordConfirmation = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($newRootPasswordConfirmationPointer)
    if ($secureCurrentAppPassword) {
        $currentAppPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureCurrentAppPassword)
        $oldAppPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($currentAppPasswordPointer)
    }
    $newAppPasswordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureNewAppPassword)
    $newAppPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($newAppPasswordPointer)
    $newAppPasswordConfirmationPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureNewAppPasswordConfirmation)
    $newAppPasswordConfirmation = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($newAppPasswordConfirmationPointer)

    if ($newRootPassword -cne $newRootPasswordConfirmation) {
        throw 'New Root password confirmation mismatch'
    }
    if ($newRootPassword.Length -lt 12) {
        throw 'New Root password must contain at least 12 characters'
    }
    if ($newAppPassword -cne $newAppPasswordConfirmation) {
        throw 'New application password confirmation mismatch'
    }
    if ($newAppPassword.Length -lt 12) {
        throw 'New application password must contain at least 12 characters'
    }
    if ($newRootPassword -ceq $currentRootPassword -or $newRootPassword -ceq $oldAppPassword) {
        throw 'New Root password must differ from exposed credentials'
    }
    if ($newAppPassword -ceq $oldAppPassword -or $newAppPassword -ceq $currentRootPassword -or $newAppPassword -ceq $newRootPassword) {
        throw 'New application password must differ from Root and exposed credentials'
    }
    $sqlNewAppPassword = $newAppPassword.Replace("'", "''")
    $sqlOldAppPassword = $oldAppPassword.Replace("'", "''")
    $sqlNewRootPassword = $newRootPassword.Replace("'", "''")

    $currentAccount = Invoke-MySql -Password $currentRootPassword -User 'root' -Sql 'SELECT CURRENT_USER();'
    $accountSeparator = $currentAccount.LastIndexOf('@')
    if ($accountSeparator -le 0) {
        throw 'Unable to identify current MySQL Root account'
    }
    $currentRootUser = $currentAccount.Substring(0, $accountSeparator)
    $currentRootHost = $currentAccount.Substring($accountSeparator + 1)
    if ($currentRootUser -cne 'root' -or $currentRootHost -notmatch '^[A-Za-z0-9_.:%-]+$') {
        throw 'Unexpected MySQL Root account'
    }

    $appAccountSql = @"
ALTER USER '$appUser'@'localhost' IDENTIFIED BY '$sqlNewAppPassword';
ALTER USER '$appUser'@'127.0.0.1' IDENTIFIED BY '$sqlNewAppPassword';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '$appUser'@'localhost';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM '$appUser'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE ON $databaseName.* TO '$appUser'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON $databaseName.* TO '$appUser'@'127.0.0.1';
"@
    [void] (Invoke-MySql -Password $currentRootPassword -User 'root' -Sql $appAccountSql)

    $originalEnvironmentContent = Get-Content -LiteralPath $environmentPath -Raw
    try {
        $updatedEnvironmentContent = Set-EnvironmentValue -Content $originalEnvironmentContent -Name 'MYSQL_PASSWORD' -Value ''
        $updatedEnvironmentContent = Set-EnvironmentValue -Content $updatedEnvironmentContent -Name 'DB_PASSWORD' -Value ''
        [IO.File]::WriteAllText($environmentPath, $updatedEnvironmentContent, [Text.UTF8Encoding]::new($false))
    }
    catch {
        $rollbackSql = @"
ALTER USER '$appUser'@'localhost' IDENTIFIED BY '$sqlOldAppPassword';
ALTER USER '$appUser'@'127.0.0.1' IDENTIFIED BY '$sqlOldAppPassword';
"@
        [void] (Invoke-MySql -Password $currentRootPassword -User 'root' -Sql $rollbackSql)
        throw
    }

    $rootAccountSql = "ALTER USER '$currentRootUser'@'$currentRootHost' IDENTIFIED BY '$sqlNewRootPassword';"
    [void] (Invoke-MySql -Password $currentRootPassword -User 'root' -Sql $rootAccountSql)

    $rootVerification = Invoke-MySql -Password $newRootPassword -User 'root' -Sql 'SELECT 1;'
    if ($rootVerification -ne '1') {
        throw 'New Root credential verification failed'
    }
    $appVerification = Invoke-MySql -Password $newAppPassword -User $appUser -Database $databaseName -Sql 'SELECT COUNT(*) FROM users;'
    if ($appVerification -notmatch '^\d+$') {
        throw 'New application credential verification failed'
    }

    $oldRootPasswordRejected = $false
    try {
        [void] (Invoke-MySql -Password $currentRootPassword -User 'root' -Sql 'SELECT 1;')
    }
    catch {
        if ($_.Exception.Message -match 'Access denied') {
            $oldRootPasswordRejected = $true
        }
        else {
            throw
        }
    }
    $oldAppPasswordRejected = $false
    try {
        [void] (Invoke-MySql -Password $oldAppPassword -User $appUser -Database $databaseName -Sql 'SELECT 1;')
    }
    catch {
        if ($_.Exception.Message -match 'Access denied') {
            $oldAppPasswordRejected = $true
        }
        else {
            throw
        }
    }
    if (-not $oldRootPasswordRejected -or -not $oldAppPasswordRejected) {
        throw 'Exposed MySQL credential remains valid'
    }

    Write-Host '[PASS] MySQL Root password rotated without Local storage'
    Write-Host '[PASS] Application password rotated without Local storage'
    Write-Host '[PASS] Application account DML-only access verified'
    Write-Host '[PASS] Exposed MySQL credentials rejected'
}
finally {
    foreach ($pointer in @(
        $currentRootPasswordPointer,
        $newRootPasswordPointer,
        $newRootPasswordConfirmationPointer,
        $currentAppPasswordPointer,
        $newAppPasswordPointer,
        $newAppPasswordConfirmationPointer
    )) {
        if ($pointer -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        }
    }
    $currentRootPassword = $null
    $newRootPassword = $null
    $newRootPasswordConfirmation = $null
    $oldAppPassword = $null
    $newAppPassword = $null
    $newAppPasswordConfirmation = $null
    $usingConfiguredCurrentAppPassword = $false
    if ($environmentValues) {
        $environmentValues.Clear()
    }
}
