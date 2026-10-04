# Agent Script 공통 환경값과 실행 파일 탐색

function Read-EnvironmentValues {
    param(
        [Parameter(Mandatory)]
        [string] $Path
    )

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^(?<name>[A-Z0-9_]+)=(?<value>.*)$') {
            $values[$Matches.name] = $Matches.value
        }
    }
    return $values
}

function Get-ConfiguredValue {
    param(
        [Parameter(Mandatory)]
        [hashtable] $Values,

        [Parameter(Mandatory)]
        [string] $Name,

        [string] $Fallback = ''
    )

    $value = $Values[$Name]
    if ($value -and $value -notmatch '^replace-with-') {
        return $value
    }
    return $Fallback
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

function Resolve-Java17Path {
    $candidates = [Collections.Generic.List[string]]::new()
    if ($env:JAVA_HOME) {
        $candidates.Add((Join-Path $env:JAVA_HOME 'bin\java.exe'))
    }

    foreach ($pathJava in Get-Command 'java.exe' -All -ErrorAction SilentlyContinue) {
        $candidates.Add($pathJava.Source)
    }

    $candidates.Add('C:\Program Files\Java\jdk-17\bin\java.exe')
    $candidates.Add('C:\Users\82109\scoop\apps\openjdk17\current\bin\java.exe')

    foreach ($candidate in $candidates | Select-Object -Unique) {
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            continue
        }
        $versionOutput = & $candidate -version 2>&1 | Out-String
        if ($LASTEXITCODE -eq 0 -and $versionOutput -match 'version "17(?:[.\-"]|$)') {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw 'Java 17 executable not found in JAVA_HOME, PATH, or known Local paths'
}
