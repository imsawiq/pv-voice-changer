<#
.SYNOPSIS
    Copies the shared sources from the root Fabric 1.21 project into every
    version fork under versions/.

.DESCRIPTION
    The root project is the single source of truth for everything except the
    small set of files that genuinely differ per Minecraft version or mod
    loader. Those live in org/sawiq/client/compat and org/sawiq/client/ui/compat
    (plus the loader entry points on NeoForge) and are never touched here.

    Files that were deleted from the root are also deleted from the forks, so a
    removed class cannot linger and keep a fork compiling against something that
    no longer exists.

.PARAMETER DryRun
    Reports what would change without writing anything.

.EXAMPLE
    ./scripts/sync-versions.ps1 -DryRun
    ./scripts/sync-versions.ps1
#>
param(
    [switch] $DryRun
)

$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$SourceRoots = @("src\main\java", "src\main\resources\assets")

# Paths, relative to a source root, that each fork owns. Everything else is
# overwritten from the root project.
$CommonExclusions = @(
    "org\sawiq\client\compat\",
    "org\sawiq\client\ui\compat\"
)
$NeoForgeExclusions = @(
    "org\sawiq\PvVoiceChanger.java",
    "org\sawiq\client\PvVoiceChangerClient.java"
)

$Forks = @(
    @{ Name = "fabric-26.1";   Loader = "fabric" }
    @{ Name = "fabric-26.2";   Loader = "fabric" }
    @{ Name = "fabric-26.3";   Loader = "fabric" }
    @{ Name = "neoforge-1.21"; Loader = "neoforge" }
    @{ Name = "neoforge-26.1"; Loader = "neoforge" }
    @{ Name = "neoforge-26.2"; Loader = "neoforge" }
    @{ Name = "neoforge-26.3"; Loader = "neoforge" }
)

function Test-IsExcluded {
    param(
        [string] $RelativePath,
        [string[]] $Exclusions
    )

    foreach ($exclusion in $Exclusions) {
        if ($exclusion.EndsWith("\")) {
            if ($RelativePath.StartsWith($exclusion, [System.StringComparison]::OrdinalIgnoreCase)) {
                return $true
            }
        } elseif ($RelativePath -ieq $exclusion) {
            return $true
        }
    }

    return $false
}

function Get-RelativeFiles {
    param([string] $BasePath)

    if (-not (Test-Path $BasePath)) {
        return @()
    }

    Get-ChildItem -Path $BasePath -Recurse -File | ForEach-Object {
        $_.FullName.Substring($BasePath.Length).TrimStart('\')
    }
}

$totalCopied = 0
$totalDeleted = 0

foreach ($fork in $Forks) {
    $forkPath = Join-Path $Root "versions\$($fork.Name)"
    if (-not (Test-Path $forkPath)) {
        throw "Version folder does not exist: $forkPath"
    }

    $exclusions = $CommonExclusions
    if ($fork.Loader -eq "neoforge") {
        $exclusions = $CommonExclusions + $NeoForgeExclusions
    }

    $copied = 0
    $deleted = 0

    foreach ($sourceRoot in $SourceRoots) {
        $sourceBase = Join-Path $Root $sourceRoot
        $targetBase = Join-Path $forkPath $sourceRoot

        $sourceFiles = Get-RelativeFiles -BasePath $sourceBase
        $targetFiles = Get-RelativeFiles -BasePath $targetBase

        foreach ($relative in $sourceFiles) {
            if (Test-IsExcluded -RelativePath $relative -Exclusions $exclusions) {
                continue
            }

            $sourceFile = Join-Path $sourceBase $relative
            $targetFile = Join-Path $targetBase $relative

            $isUpToDate = (Test-Path $targetFile) -and
                ((Get-FileHash $sourceFile).Hash -eq (Get-FileHash $targetFile).Hash)
            if ($isUpToDate) {
                continue
            }

            Write-Host "  copy   $($fork.Name)  $relative"
            if (-not $DryRun) {
                $targetDirectory = Split-Path -Parent $targetFile
                New-Item -ItemType Directory -Force $targetDirectory | Out-Null
                Copy-Item -Force $sourceFile $targetFile
            }
            $copied++
        }

        # Anything the fork still has that the root no longer ships, and that
        # the fork does not own, is stale and has to go.
        foreach ($relative in $targetFiles) {
            if (Test-IsExcluded -RelativePath $relative -Exclusions $exclusions) {
                continue
            }
            if ($sourceFiles -contains $relative) {
                continue
            }

            Write-Host "  delete $($fork.Name)  $relative"
            if (-not $DryRun) {
                Remove-Item -Force (Join-Path $targetBase $relative)
            }
            $deleted++
        }
    }

    Write-Host "$($fork.Name): $copied copied, $deleted deleted"
    $totalCopied += $copied
    $totalDeleted += $deleted
}

if ($DryRun) {
    Write-Host "Dry run: $totalCopied files would be copied, $totalDeleted deleted."
} else {
    Write-Host "Synced $totalCopied files, deleted $totalDeleted."
}
