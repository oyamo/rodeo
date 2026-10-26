param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('install', 'upgrade', 'uninstall', 'status')]
    [string]$Action,

    [string]$Jar,
    [string]$Config,
    [switch]$Purge
)

$ErrorActionPreference = 'Stop'

$ServiceName = 'rodeo'
$Description = 'rodeo pipeline runner'
$JarName = 'rodeo.jar'
$ExampleConfigName = 'config.example.toml'
$NssmName = 'nssm.exe'
$InstallDir = Join-Path $env:ProgramFiles 'rodeo'
$DataDir = Join-Path $env:ProgramData 'rodeo'
$ConfigFile = Join-Path $DataDir 'config.toml'
$InstalledJar = Join-Path $InstallDir $JarName
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path

$ServiceSettings = [ordered]@{
    Description = $Description
    AppDirectory = $InstallDir
    Start = 'SERVICE_AUTO_START'
    AppExit = 'Default Restart'
}

function Require-Administrator {
    $principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'run this script from an elevated PowerShell window'
    }
}

function Get-Nssm {
    $bundled = Join-Path $Here $NssmName
    if (Test-Path $bundled) {
        return $bundled
    }
    $installed = Get-Command nssm -ErrorAction SilentlyContinue
    if ($installed) {
        return $installed.Source
    }
    throw 'NSSM is required to register the service. Install it with: winget install --id NSSM.NSSM -e'
}

function Get-JavaPath {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if (-not $java) {
        throw 'Java 21 or newer is required. Install it with: winget install --id EclipseAdoptium.Temurin.21.JDK -e'
    }
    return $java.Source
}

function Require-Jar {
    if (-not $Jar) { throw '-Jar PATH is required' }
    if (-not (Test-Path $Jar)) { throw "no such jar: $Jar" }
}

function Copy-Jar {
    if ((Resolve-Path $Jar).Path -ne $InstalledJar) {
        Copy-Item -Force -Path $Jar -Destination $InstalledJar
    }
}

function Install-Config {
    if (Test-Path $ConfigFile) {
        return
    }
    if ($Config) {
        Copy-Item -Path $Config -Destination $ConfigFile
    } else {
        Copy-Item -Path (Join-Path $Here $ExampleConfigName) -Destination $ConfigFile
        Write-Host "edit $ConfigFile and set the webhook secrets before starting rodeo"
    }
}

function Register-Service {
    param([string]$Nssm, [string]$JavaPath)
    $arguments = "-jar `"$InstalledJar`" --rodeo.config=`"$ConfigFile`""
    & $Nssm install $ServiceName $JavaPath $arguments | Out-Null
    foreach ($setting in $ServiceSettings.GetEnumerator()) {
        & $Nssm set $ServiceName $setting.Key $setting.Value | Out-Null
    }
}

function Install-Rodeo {
    Require-Administrator
    Require-Jar
    $nssm = Get-Nssm
    $javaPath = Get-JavaPath
    New-Item -ItemType Directory -Force -Path $InstallDir, $DataDir | Out-Null
    Copy-Jar
    Install-Config
    Register-Service -Nssm $nssm -JavaPath $javaPath
    & $nssm start $ServiceName
}

function Upgrade-Rodeo {
    Require-Administrator
    Require-Jar
    $nssm = Get-Nssm
    & $nssm stop $ServiceName | Out-Null
    Copy-Jar
    & $nssm start $ServiceName
}

function Uninstall-Rodeo {
    Require-Administrator
    $nssm = Get-Nssm
    & $nssm stop $ServiceName | Out-Null
    & $nssm remove $ServiceName confirm | Out-Null
    Remove-Item -Recurse -Force -Path $InstallDir -ErrorAction SilentlyContinue
    if ($Purge) {
        Remove-Item -Recurse -Force -Path $DataDir -ErrorAction SilentlyContinue
    } else {
        Write-Host "kept $DataDir; pass -Purge to remove it"
    }
}

function Show-Status {
    Get-Service -Name $ServiceName
}

switch ($Action) {
    'install' { Install-Rodeo }
    'upgrade' { Upgrade-Rodeo }
    'uninstall' { Uninstall-Rodeo }
    'status' { Show-Status }
}
