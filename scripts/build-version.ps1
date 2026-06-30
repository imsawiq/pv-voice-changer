param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string] $Version,

    [switch] $NoClean
)

$ErrorActionPreference = "Stop"

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Targets = @{
    "26.1"          = @{ Path = (Join-Path $Root "versions\fabric-26.1"); Version = "fabric-26.1"; Buildable = $true }
    "fabric-26.1"   = @{ Path = (Join-Path $Root "versions\fabric-26.1"); Version = "fabric-26.1"; Buildable = $true }
    "26.2"          = @{ Path = (Join-Path $Root "versions\fabric-26.2"); Version = "fabric-26.2"; Buildable = $true }
    "fabric-26.2"   = @{ Path = (Join-Path $Root "versions\fabric-26.2"); Version = "fabric-26.2"; Buildable = $true }
    "neoforge-1.21" = @{ Path = (Join-Path $Root "versions\neoforge-1.21"); Version = "neoforge-1.21"; Buildable = $true }
    "nf-1.21"       = @{ Path = (Join-Path $Root "versions\neoforge-1.21"); Version = "neoforge-1.21"; Buildable = $true }
    "neoforge-26.1" = @{ Path = (Join-Path $Root "versions\neoforge-26.1"); Version = "neoforge-26.1"; Buildable = $true }
    "nf-26.1"       = @{ Path = (Join-Path $Root "versions\neoforge-26.1"); Version = "neoforge-26.1"; Buildable = $true }
    "neoforge-26.2" = @{ Path = (Join-Path $Root "versions\neoforge-26.2"); Version = "neoforge-26.2"; Buildable = $true }
    "nf-26.2"       = @{ Path = (Join-Path $Root "versions\neoforge-26.2"); Version = "neoforge-26.2"; Buildable = $true }
}

if ($Version -eq "all") {
    & (Join-Path $PSScriptRoot "build-all.ps1") -NoClean:$NoClean
    exit $LASTEXITCODE
}

if (-not $Targets.ContainsKey($Version)) {
    $knownVersions = ($Targets.Keys | Sort-Object) -join ", "
    throw "Unknown version '$Version'. Known versions: $knownVersions, all"
}

$Target = $Targets[$Version]
$ProjectDir = $Target.Path
$OutputVersion = $Target.Version

if (-not $Target.Buildable) {
    Write-Host "$OutputVersion is a porting scaffold and is not included in automated builds yet."
    Write-Host "Finish Gradle versions/dependencies in $ProjectDir before enabling it in this script."
    exit 0
}

if (-not (Test-Path $ProjectDir)) {
    throw "Version folder does not exist: $ProjectDir"
}

$GradleBat = Join-Path $ProjectDir "gradlew.bat"
if (-not (Test-Path $GradleBat)) {
    throw "Gradle wrapper was not found: $GradleBat"
}

$OldJavaHome = $env:JAVA_HOME
$OldPath = $env:Path
$Jdk25 = "C:\Users\sawiq\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2"

try {
    $GradleProperties = Join-Path $ProjectDir "gradle.properties"
    $UsesPinnedJava = (Test-Path $GradleProperties) -and ((Get-Content $GradleProperties) -match "^org\.gradle\.java\.home=")

    if (-not $UsesPinnedJava -and (Test-Path $Jdk25)) {
        $env:JAVA_HOME = $Jdk25
        $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    }

    $GradleArgs = @()
    if (-not $NoClean) {
        $GradleArgs += "clean"
    }
    $GradleArgs += "build"

    Write-Host "Building $OutputVersion in $ProjectDir"
    Push-Location $ProjectDir
    try {
        & $GradleBat @GradleArgs
        $ExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }

    if ($ExitCode -ne 0) {
        exit $ExitCode
    }

    $DistDir = Join-Path $Root "dist"
    New-Item -ItemType Directory -Force $DistDir | Out-Null
    Get-ChildItem $DistDir -Filter "*-$OutputVersion*.jar" -File | Remove-Item -Force

    $JarFiles = Get-ChildItem (Join-Path $ProjectDir "build\libs") -Filter "*.jar" -File
    foreach ($JarFile in $JarFiles) {
        $OutputName = if ($JarFile.BaseName.EndsWith("-sources")) {
            $BaseName = $JarFile.BaseName.Substring(0, $JarFile.BaseName.Length - "-sources".Length)
            "$BaseName-$OutputVersion-sources$($JarFile.Extension)"
        } else {
            "$($JarFile.BaseName)-$OutputVersion$($JarFile.Extension)"
        }

        Copy-Item -Force $JarFile.FullName (Join-Path $DistDir $OutputName)
    }

    Write-Host "Copied versioned jars to $DistDir"
} finally {
    $env:JAVA_HOME = $OldJavaHome
    $env:Path = $OldPath
}
