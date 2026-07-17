param(
    [switch]$CleanEmulator,
    [switch]$Headless,
    [switch]$NoSnapshotLoad = $true,
    [string]$AvdName = "VendaMais_API_35",
    [string]$PackageName = "br.com.vendamais.mobile",
    [string]$ActivityName = ".MainActivity",
    [string]$InstallTask = ":app:installDebug",
    [string]$DeepLinkUrl = "",
    [int]$BootTimeoutSec = 420
)

$ErrorActionPreference = "Stop"

$runnerPath = Join-Path $PSScriptRoot "android-app\run-android.ps1"
if (-not (Test-Path $runnerPath)) {
    throw "Script nao encontrado: $runnerPath"
}

& $runnerPath `
    -AvdName $AvdName `
    -PackageName $PackageName `
    -ActivityName $ActivityName `
    -InstallTask $InstallTask `
    -DeepLinkUrl $DeepLinkUrl `
    -BootTimeoutSec $BootTimeoutSec `
    -CleanEmulator:$CleanEmulator `
    -Headless:$Headless `
    -NoSnapshotLoad:$NoSnapshotLoad
