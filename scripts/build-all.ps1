param(
    [switch] $NoClean
)

$ErrorActionPreference = "Stop"
$Versions = @("fabric-26.1", "fabric-26.2", "neoforge-1.21", "neoforge-26.1", "neoforge-26.2")

foreach ($Version in $Versions) {
    & (Join-Path $PSScriptRoot "build-version.ps1") $Version -NoClean:$NoClean
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
