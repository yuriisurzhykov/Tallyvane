[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [Parameter(Position = 0)]
    [ValidateSet('up', 'rebuild', 'start', 'down', 'reset-data', 'status', 'logs', 'stats')]
    [string] $Command = 'up',

    [Parameter(Position = 1)]
    [ValidateSet('backend', 'frontend-web', 'frontend-app', 'frontend-admin', 'all')]
    [string] $Target = 'all',

    [Parameter()]
    [ValidatePattern('^[1-9][0-9]*(k|m|g)?$')]
    [string] $BuildMemoryLimit = '1536m',

    [Parameter()]
    [switch] $Follow
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $PSScriptRoot 'docker-compose.auth-demo.yml'
$localEnvFile = Join-Path $PSScriptRoot 'auth-local.env'
$sampleEnvFile = Join-Path $PSScriptRoot 'auth-local.env.example'
$keysetPath = Join-Path $env:LOCALAPPDATA 'Tallyvane\auth-local\totp-keyset.json'
$initialTotpKeyset = $env:DEMO_TOTP_KEYSET
$script:composeEnvFile = $null
$script:composePrefix = $null

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory)]
        [string] $Executable,

        [Parameter(Mandatory)]
        [string[]] $Arguments
    )

    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command '$Executable $($Arguments -join ' ')' failed with exit code $LASTEXITCODE."
    }
}

function Invoke-Compose {
    param(
        [Parameter(Mandatory)]
        [string[]] $Arguments
    )

    Invoke-NativeCommand -Executable 'docker' -Arguments (@('compose') + $script:composePrefix + $Arguments)
}

function Set-ComposeInputs {
    param(
        [Parameter(Mandatory)]
        [bool] $RequiresRuntimeSecrets
    )

    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker CLI was not found. Install Docker Desktop and make sure docker is on PATH.'
    }

    if ($RequiresRuntimeSecrets) {
        if (-not (Test-Path -LiteralPath $localEnvFile -PathType Leaf)) {
            throw "Local env file is missing: $localEnvFile. Copy ops/auth-local.env.example to ops/auth-local.env and set local options."
        }

        if ([string]::IsNullOrWhiteSpace($env:DEMO_TOTP_KEYSET)) {
            if (-not (Test-Path -LiteralPath $keysetPath -PathType Leaf)) {
                throw "Local TOTP keyset is missing: $keysetPath. Follow section 4 of docs/operations/auth-local-development.md."
            }

            $env:DEMO_TOTP_KEYSET = (Get-Content -LiteralPath $keysetPath -Raw).Trim()
        }

        if ([string]::IsNullOrWhiteSpace($env:DEMO_TOTP_KEYSET)) {
            throw "The local TOTP keyset file is empty: $keysetPath."
        }

        $script:composeEnvFile = $localEnvFile
    }
    elseif (Test-Path -LiteralPath $localEnvFile -PathType Leaf) {
        $script:composeEnvFile = $localEnvFile
        if ([string]::IsNullOrWhiteSpace($env:DEMO_TOTP_KEYSET)) {
            # Compose parses the full model even for read-only lifecycle commands.
            # This placeholder is never passed to a container by these commands.
            $env:DEMO_TOTP_KEYSET = 'lifecycle-command-only'
        }
    }
    else {
        $script:composeEnvFile = $sampleEnvFile
        if ([string]::IsNullOrWhiteSpace($env:DEMO_TOTP_KEYSET)) {
            $env:DEMO_TOTP_KEYSET = 'lifecycle-command-only'
        }
    }

    if (-not (Test-Path -LiteralPath $composeFile -PathType Leaf)) {
        throw "Local Compose file is missing: $composeFile."
    }

    $script:composePrefix = @('--env-file', $script:composeEnvFile, '-f', $composeFile)
}

function Get-WritableGradleUserHome {
    $gradleUserHome = $env:GRADLE_USER_HOME
    if ([string]::IsNullOrWhiteSpace($gradleUserHome)) {
        if ([string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
            throw 'USERPROFILE is not set, so a persistent Gradle user home cannot be selected.'
        }

        $gradleUserHome = Join-Path $env:USERPROFILE '.gradle'
        $env:GRADLE_USER_HOME = $gradleUserHome
    }

    New-Item -ItemType Directory -Force -Path $gradleUserHome | Out-Null
    $probePath = Join-Path $gradleUserHome ".local-stack-$([guid]::NewGuid().ToString('N')).tmp"
    try {
        [System.IO.File]::WriteAllText($probePath, '')
    }
    catch {
        throw "GRADLE_USER_HOME is not writable: $gradleUserHome. Set GRADLE_USER_HOME to a persistent writable directory."
    }
    finally {
        Remove-Item -LiteralPath $probePath -Force -ErrorAction SilentlyContinue
    }

    return $gradleUserHome
}

function Build-BackendDistribution {
    $gradleUserHome = Get-WritableGradleUserHome
    Write-Host "Building backend distributions with GRADLE_USER_HOME=$gradleUserHome"
    Push-Location (Join-Path $repoRoot 'backend')
    try {
        Invoke-NativeCommand -Executable '.\gradlew.bat' -Arguments @(
            ':server:installDist',
            ':migrate:installDist',
            '--no-daemon',
            '--max-workers=2'
        )
    }
    finally {
        Pop-Location
    }
}

function Build-SelectedImages {
    param(
        [Parameter(Mandatory)]
        [ValidateSet('backend', 'frontend-web', 'frontend-app', 'frontend-admin', 'all')]
        [string] $BuildTarget
    )

    $services = switch ($BuildTarget) {
        'backend' { @('server') }
        'frontend-web' { @('frontend-web') }
        'frontend-app' { @('frontend-app') }
        'frontend-admin' { @('frontend-admin') }
        'all' { @('server', 'frontend-web', 'frontend-app', 'frontend-admin') }
    }

    # Check the cached dependency layers before stopping running containers.
    # Missing workspace manifests make pnpm reinstall after COPY . . in a non-TTY build.
    $workspaceManifests = @(
        Get-ChildItem -LiteralPath $repoRoot -Directory -Name |
            Where-Object { $_ -like 'frontend-*' } |
            ForEach-Object { "$_/package.json" }
        Get-ChildItem -LiteralPath (Join-Path $repoRoot 'packages') -Directory -Name |
            ForEach-Object { "packages/$_/package.json" }
    ) | Where-Object { Test-Path -LiteralPath (Join-Path $repoRoot $_) }
    foreach ($service in @($services | Where-Object { $_ -like 'frontend-*' })) {
        $dockerfile = Get-Content -LiteralPath (Join-Path $repoRoot "$service/Dockerfile") -Raw
        foreach ($manifest in $workspaceManifests) {
            if (-not $dockerfile.Contains("COPY $manifest ")) {
                throw "$service/Dockerfile does not copy $manifest into its deps stage. Update the Dockerfile before building."
            }
        }
    }

    foreach ($service in $services) {
        if ($service -eq 'server') {
            Build-BackendDistribution
        }

        Write-Host "Building image for $service"
        Invoke-Compose -Arguments @('build', '--memory', $BuildMemoryLimit, $service)
    }
}

function Get-RunningComposeServices {
    $runningServices = @(& docker compose @script:composePrefix ps --services --status running)
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose ps failed with exit code $LASTEXITCODE."
    }

    return @($runningServices | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Wait-ForFrontendServices {
    $services = @('frontend-web', 'frontend-app', 'frontend-admin')
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    $pending = @($services)

    while ($pending.Count -gt 0 -and [DateTime]::UtcNow -lt $deadline) {
        $pending = @()
        foreach ($service in $services) {
            $containerId = @(& docker compose @script:composePrefix ps -q $service | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -First 1)
            if ($LASTEXITCODE -ne 0) {
                throw "Could not inspect frontend service '$service'."
            }
            if ($containerId.Count -eq 0) {
                $pending += $service
                continue
            }

            $state = (& docker inspect --format '{{.State.Status}}' $containerId[0]).Trim()
            if ($LASTEXITCODE -ne 0) {
                throw "Could not read container state for frontend service '$service'."
            }
            $health = (& docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' $containerId[0]).Trim()
            if ($LASTEXITCODE -ne 0) {
                throw "Could not read health status for frontend service '$service'."
            }

            if ($state -ne 'running' -or $health -eq 'unhealthy' -or $health -eq 'missing') {
                Write-Host "Frontend service '$service' failed startup (state=$state, health=$health). Recent logs:"
                & docker compose @script:composePrefix logs --tail 80 $service
                throw "Frontend service '$service' did not start healthy."
            }
            if ($health -ne 'healthy') {
                $pending += $service
            }
        }

        if ($pending.Count -gt 0) {
            Start-Sleep -Seconds 2
        }
    }

    if ($pending.Count -gt 0) {
        foreach ($service in $pending) {
            Write-Host "Frontend service '$service' did not become healthy. Recent logs:"
            & docker compose @script:composePrefix logs --tail 80 $service
        }
        throw "Frontend startup health check timed out for: $($pending -join ', ')."
    }

    Write-Host 'All frontend containers passed their HTTP health checks.'
}

function Invoke-StackBuild {
    param(
        [Parameter(Mandatory)]
        [ValidateSet('backend', 'frontend-web', 'frontend-app', 'frontend-admin', 'all')]
        [string] $BuildTarget
    )

    $previouslyRunningServices = @(Get-RunningComposeServices)
    try {
        if ($previouslyRunningServices.Count -gt 0) {
            Write-Host 'Stopping the local stack during image builds to release its WSL memory; named volumes stay attached.'
            Invoke-Compose -Arguments (@('stop') + $previouslyRunningServices)
        }

        Build-SelectedImages -BuildTarget $BuildTarget
    }
    catch {
        $buildError = $_
        if ($previouslyRunningServices.Count -gt 0) {
            Write-Warning 'A build failed. Restarting the pre-build containers without recreating them.'
            try {
                Invoke-Compose -Arguments (@('up', '-d', '--no-build', '--no-recreate') + $previouslyRunningServices)
            }
            catch {
                Write-Warning "Could not automatically restart the previous stack: $($_.Exception.Message)"
            }
        }

        throw $buildError
    }

    Invoke-Compose -Arguments @('up', '-d', '--no-build', '--remove-orphans')
    Wait-ForFrontendServices
}

Push-Location $repoRoot
try {
    $needsRuntimeSecrets = $Command -in @('up', 'rebuild', 'start')
    Set-ComposeInputs -RequiresRuntimeSecrets $needsRuntimeSecrets
    if ($needsRuntimeSecrets) {
        Write-Host 'Checking Docker Engine before starting build work.'
        Invoke-NativeCommand -Executable 'docker' -Arguments @('info', '--format', '{{.ServerVersion}}')
    }

    switch ($Command) {
        'up' {
            Invoke-Compose -Arguments @('config', '--quiet')
            Invoke-StackBuild -BuildTarget 'all'
        }
        'rebuild' {
            Invoke-Compose -Arguments @('config', '--quiet')
            Invoke-StackBuild -BuildTarget $Target
        }
        'start' {
            Invoke-Compose -Arguments @('config', '--quiet')
            Invoke-Compose -Arguments @('up', '-d', '--no-build', '--remove-orphans')
            Wait-ForFrontendServices
        }
        'down' {
            Invoke-Compose -Arguments @('down')
        }
        'reset-data' {
            if ($PSCmdlet.ShouldProcess('auth-demo PostgreSQL and Mailpit volumes', 'Remove')) {
                Invoke-Compose -Arguments @('down', '--volumes')
            }
        }
        'status' {
            Invoke-Compose -Arguments @('ps', '--all')
        }
        'logs' {
            $logArguments = @('logs', '--tail', '100')
            if ($Follow) {
                $logArguments += '--follow'
            }
            Invoke-Compose -Arguments $logArguments
        }
        'stats' {
            $containerIds = @(& docker compose @script:composePrefix ps -q --status running)
            if ($LASTEXITCODE -ne 0) {
                throw "docker compose ps failed with exit code $LASTEXITCODE."
            }

            $containerIds = @($containerIds | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
            if ($containerIds.Count -eq 0) {
                Write-Host 'No auth-demo containers are running.'
            }
            else {
                Invoke-NativeCommand -Executable 'docker' -Arguments (@(
                    'stats', '--no-stream', '--format', 'table {{.Name}} {{.MemUsage}} {{.MemPerc}}'
                ) + $containerIds)
            }
        }
    }
}
catch {
    Write-Error $_
    exit 1
}
finally {
    Pop-Location
    $savedWhatIfPreference = $WhatIfPreference
    $WhatIfPreference = $false
    try {
        if ($null -eq $initialTotpKeyset) {
            Remove-Item Env:DEMO_TOTP_KEYSET -ErrorAction SilentlyContinue
        }
        else {
            $env:DEMO_TOTP_KEYSET = $initialTotpKeyset
        }
    }
    finally {
        $WhatIfPreference = $savedWhatIfPreference
    }
}
