# 변경 범위별 Harness 검증
param(
    [ValidateSet('changed', 'frontend', 'backend', 'infra', 'docs', 'all')]
    [string]$Scope = 'changed'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$frontendRoot = Join-Path $repoRoot 'frontend'
$backendRoot = Join-Path $repoRoot 'backend'
$temporaryBuildRoot = Join-Path $repoRoot 'output\agent-verify\frontend-dist'

function Invoke-CheckedCommand {
    param(
        [string]$Name,
        [string]$WorkingDirectory,
        [string]$Command,
        [string[]]$Arguments
    )

    Write-Host "[RUN] $Name"
    Push-Location $WorkingDirectory
    try {
        & $Command @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$Name failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
    Write-Host "[PASS] $Name"
}

function Get-NpmCommand {
    $npmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if (-not $npmCommand) {
        $npmCommand = Get-Command npm -ErrorAction SilentlyContinue
    }
    if (-not $npmCommand) {
        return $null
    }

    & $npmCommand.Source --version *> $null
    if ($LASTEXITCODE -ne 0) {
        return $null
    }
    return $npmCommand.Source
}

function Invoke-FrontendVerification {
    $npmCommand = Get-NpmCommand
    if ($npmCommand) {
        Invoke-CheckedCommand 'Frontend lint' $frontendRoot $npmCommand @('run', 'lint')
        Invoke-CheckedCommand 'Frontend unit test' $frontendRoot $npmCommand @('test')
        Invoke-CheckedCommand 'Frontend build' $frontendRoot $npmCommand @('run', 'build')
        return
    }

    Write-Warning 'npm launcher unavailable; using installed project binaries'
    $binRoot = Join-Path $frontendRoot 'node_modules\.bin'
    $commandSuffix = if ($IsWindows) { '.cmd' } else { '' }
    $eslint = Join-Path $binRoot "eslint$commandSuffix"
    $vitest = Join-Path $binRoot "vitest$commandSuffix"
    $tsc = Join-Path $binRoot "tsc$commandSuffix"
    $vite = Join-Path $binRoot "vite$commandSuffix"

    foreach ($requiredCommand in @($eslint, $vitest, $tsc, $vite)) {
        if (-not (Test-Path $requiredCommand)) {
            throw '[정상 npm 실행 경로 또는 설치된 Frontend 의존성이 필요합니다.]'
        }
    }

    Invoke-CheckedCommand 'Frontend lint fallback' $frontendRoot $eslint @('.')
    Invoke-CheckedCommand 'Frontend unit test fallback' $frontendRoot $vitest @('run', '--configLoader', 'runner')
    Invoke-CheckedCommand 'Frontend app type check fallback' $frontendRoot $tsc @('--noEmit', '-p', 'tsconfig.app.json')
    Invoke-CheckedCommand 'Frontend config type check fallback' $frontendRoot $tsc @('--noEmit', '-p', 'tsconfig.node.json')
    Invoke-CheckedCommand 'Frontend Vite build fallback' $frontendRoot $vite @('build', '--configLoader', 'runner', '--outDir', $temporaryBuildRoot, '--emptyOutDir')
}

function Invoke-BackendVerification {
    $mavenWrapperCmd = Join-Path $backendRoot 'mvnw.cmd'
    $mavenWrapper = Join-Path $backendRoot 'mvnw'
    $wrapperJar = Join-Path $backendRoot '.mvn\wrapper\maven-wrapper.jar'

    if ($IsWindows -and (Test-Path $mavenWrapperCmd)) {
        Invoke-CheckedCommand 'Backend Maven verify' $backendRoot $mavenWrapperCmd @('-B', '-ntp', 'verify')
        return
    }
    if ((-not $IsWindows) -and (Test-Path $mavenWrapper)) {
        Invoke-CheckedCommand 'Backend Maven verify' $backendRoot $mavenWrapper @('-B', '-ntp', 'verify')
        return
    }
    if (Test-Path $wrapperJar) {
        Invoke-CheckedCommand 'Backend Maven verify with wrapper JAR' $backendRoot 'java' @(
            "-Dmaven.multiModuleProjectDirectory=$backendRoot",
            '-classpath',
            $wrapperJar,
            'org.apache.maven.wrapper.MavenWrapperMain',
            '-B',
            '-ntp',
            'verify'
        )
        return
    }

    $maven = Get-Command mvn -ErrorAction SilentlyContinue
    if ($maven) {
        Invoke-CheckedCommand 'Backend Maven verify' $backendRoot $maven.Source @('-B', '-ntp', 'verify')
        return
    }
    throw '[Maven 또는 완전한 Maven Wrapper 실행 파일이 필요합니다.]'
}

function Invoke-InfrastructureVerification {
    $schemaDirectory = Join-Path $repoRoot 'infra\mysql\schema'
    $schemaFiles = @(Get-ChildItem -LiteralPath $schemaDirectory -Filter 'V????__*.sql' -File -ErrorAction SilentlyContinue)
    if ($schemaFiles.Count -eq 0) {
        Write-Host '[NOT APPLICABLE] Schema SQL: no local initial or Git-managed follow-up files'
    }
    foreach ($schemaFile in $schemaFiles) {
        $schemaSql = Get-Content -LiteralPath $schemaFile.FullName -Raw
        foreach ($requiredPattern in @('-- 이유:', '-- 내용:', '-- 호환성:', '-- Rollback:')) {
            if ($schemaSql -notmatch [regex]::Escape($requiredPattern)) {
                throw "Git Schema SQL metadata missing in $($schemaFile.Name): $requiredPattern"
            }
        }
        if ($schemaSql -match '(?i)schema_change_history') {
            throw "Database-side Schema history is not allowed: $($schemaFile.Name)"
        }
    }
    if ($schemaFiles.Count -gt 0) {
        Write-Host '[PASS] Native MySQL Schema SQL structure'
    }

    $composePath = Join-Path $repoRoot 'docker-compose.yml'
    if (Test-Path -LiteralPath $composePath -PathType Leaf) {
        $verificationEnvironment = @{
            REDIS_PASSWORD = 'test-redis-password'
            GRAFANA_ADMIN_PASSWORD = 'test-grafana-password'
        }
        $previousEnvironment = @{}
        foreach ($name in $verificationEnvironment.Keys) {
            $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
            if ([string]::IsNullOrWhiteSpace($previousEnvironment[$name])) {
                [Environment]::SetEnvironmentVariable($name, $verificationEnvironment[$name], 'Process')
            }
        }
        try {
            Invoke-CheckedCommand 'Docker Compose configuration' $repoRoot 'docker' @(
                'compose',
                'config',
                '--quiet'
            )
        }
        finally {
            foreach ($name in $verificationEnvironment.Keys) {
                [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
            }
        }
    }
    else {
        Write-Host '[NOT RUN] Docker Compose configuration: Local-only YAML unavailable'
    }
}

function Invoke-DocsVerification {
    $requiredFiles = @(
        'AGENTS.md',
        '.ai\MEMORY.md',
        '.ai\RULES.md',
        '.ai\PLAN.md',
        'docs\agent\project-context.md',
        'docs\agent\workflow.md',
        'docs\agent\verification.md',
        'docs\agent\code-review.md',
        'docs\agent\backend-review.md'
    )
    foreach ($relativePath in $requiredFiles) {
        $path = Join-Path $repoRoot $relativePath
        if (-not (Test-Path $path -PathType Leaf)) {
            throw "Required Harness file missing: $relativePath"
        }
    }
    Write-Host '[PASS] Harness file structure'
}

function Invoke-RepositoryExposureVerification {
    $trackedPaths = & git -c "safe.directory=$($repoRoot.Replace('\', '/'))" -C $repoRoot ls-files --cached --others --exclude-standard
    if ($LASTEXITCODE -ne 0) {
        throw '[Local 설정 추적 여부를 확인할 수 있는 Git 작업 트리가 필요합니다.]'
    }

    $forbiddenPaths = foreach ($trackedPath in $trackedPaths) {
        $path = $trackedPath.Replace('\', '/')
        $isEnvironmentFile = $path -match '(^|/)\.env($|\.)' -and $path -notmatch '\.env\.(example|template)$'
        $isLocalConfig = $path -match '(^|/)(local\.properties|\.java-version\.local|\.tool-versions\.local)$' -or
            $path -match '\.local\.(json|ya?ml|properties|toml)$' -or
            $path -match '(^|/)application[^/]*(local|secret)[^/]*\.(ya?ml|properties)$'
        $isCredentialFile = $path -match '(^|/)secrets?/' -or
            $path -match '(^|/)(credentials|service-account)[^/]*\.json$' -or
            $path -match '\.(pem|key|p12|pfx|jks|keystore|der)$' -or
            $path -match '(^|/)\.npmrc$'

        if ($isEnvironmentFile -or $isLocalConfig -or $isCredentialFile) {
            $path
        }
    }

    if ($forbiddenPaths) {
        throw "Git-tracked Local or Secret configuration detected: $($forbiddenPaths -join ', ')"
    }
    Write-Host '[PASS] Repository candidate Local and Secret configuration check'
}

function Invoke-SecretValueVerification {
    $candidatePaths = & git -c "safe.directory=$($repoRoot.Replace('\', '/'))" -C $repoRoot ls-files --cached --others --exclude-standard
    if ($LASTEXITCODE -ne 0) {
        throw '[보안값 포함 여부를 확인할 수 있는 Git 작업 트리가 필요합니다.]'
    }

    $binaryExtensions = @('.jar', '.png', '.jpg', '.jpeg', '.gif', '.ico', '.pdf', '.zip', '.gz', '.woff', '.woff2')
    $assignmentPattern = '(?im)^[ \t]*["'']?(?<name>[A-Z][A-Z0-9_.-]*(?:API_KEY|CLIENT_SECRET|APP_PASSWORD|ACCESS_TOKEN|PRIVATE_KEY|SECRET|PASSWORD|TOKEN)|(?:api[-_.]?key|client[-_.]?secret|app[-_.]?password))["'']?[ \t]*[:=][ \t]*["'']?(?<value>[^"'' \t\r\n,#}]+)'
    $knownSecretPatterns = @(
        'AIza[0-9A-Za-z_-]{35}',
        'AKIA[0-9A-Z]{16}',
        'gh[pousr]_[0-9A-Za-z]{30,}',
        'xox[baprs]-[0-9A-Za-z-]{20,}',
        '-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'
    )
    $detectedPaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)

    foreach ($candidatePath in $candidatePaths) {
        $absolutePath = Join-Path $repoRoot $candidatePath
        $fileInfo = Get-Item -LiteralPath $absolutePath -Force -ErrorAction SilentlyContinue
        if ($null -eq $fileInfo -or $fileInfo.PSIsContainer) {
            continue
        }
        if ([IO.Path]::GetExtension($candidatePath).ToLowerInvariant() -in $binaryExtensions) {
            continue
        }
        if ($fileInfo.Length -gt 2MB) {
            continue
        }

        $content = Get-Content -LiteralPath $absolutePath -Raw -ErrorAction SilentlyContinue
        if ($null -eq $content) {
            continue
        }

        foreach ($match in [regex]::Matches($content, $assignmentPattern)) {
            $value = $match.Groups['value'].Value
            if ($value -notmatch '^(\$\{|replace-with-|<|example|dummy|test|\.\.\.)') {
                [void]$detectedPaths.Add($candidatePath)
            }
        }
        foreach ($knownSecretPattern in $knownSecretPatterns) {
            if ($content -match $knownSecretPattern) {
                [void]$detectedPaths.Add($candidatePath)
            }
        }
    }

    if ($detectedPaths.Count -gt 0) {
        throw "Possible Secret value detected in Repository candidate files: $($detectedPaths -join ', ')"
    }
    Write-Host '[PASS] Repository candidate Secret value check'
}

function Get-ChangedScopes {
    $status = & git -c "safe.directory=$($repoRoot.Replace('\', '/'))" -C $repoRoot status --porcelain=v1 --untracked-files=all
    if ($LASTEXITCODE -ne 0) {
        throw '[변경 파일 기반 검증을 위해 읽을 수 있는 Git 작업 트리가 필요합니다.]'
    }

    $scopes = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($line in $status) {
        if ($line.Length -lt 4) {
            continue
        }
        $path = $line.Substring(3).Replace('\', '/')
        if ($path.StartsWith('frontend/')) { [void]$scopes.Add('frontend') }
        elseif ($path.StartsWith('backend/')) { [void]$scopes.Add('backend') }
        elseif ($path.StartsWith('infra/') -or $path -in @('docker-compose.yml', '.env.example')) { [void]$scopes.Add('infra') }
        else { [void]$scopes.Add('docs') }
    }
    if ($scopes.Count -eq 0) {
        [void]$scopes.Add('docs')
    }
    return $scopes
}

$selectedScopes = switch ($Scope) {
    'all' { @('docs', 'frontend', 'backend', 'infra') }
    'changed' { @(Get-ChangedScopes) }
    default { @($Scope) }
}

foreach ($selectedScope in $selectedScopes) {
    switch ($selectedScope) {
        'docs' { Invoke-DocsVerification }
        'frontend' { Invoke-FrontendVerification }
        'backend' { Invoke-BackendVerification }
        'infra' { Invoke-InfrastructureVerification }
    }
}

Invoke-RepositoryExposureVerification
Invoke-SecretValueVerification

Invoke-CheckedCommand 'Git diff whitespace check' $repoRoot 'git' @(
    '-c',
    "safe.directory=$($repoRoot.Replace('\', '/'))",
    '-C',
    $repoRoot,
    'diff',
    '--check'
)

Write-Host "[PASS] Verification scope: $($selectedScopes -join ', ')"
