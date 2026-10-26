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
$InstallDir = Join-Path $env:ProgramFiles 'rodeo'
$DataDir = Join-Path $env:ProgramData 'rodeo'
$ConfigFile = Join-Path $DataDir 'config.toml'
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path

function Require-Administrator {
    $principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'run this script from an elevated PowerShell window'
    }
}

function Require-Nssm {
    $bundled = Join-Path $Here 'nssm.exe'
    $installed = Get-Command nssm -ErrorAction SilentlyContinue
    if (Test-Path $bundled) {
        $script:Nssm = $bundled
    } elseif ($installed) {
        $script:Nssm = $installed.Source
    } else {
        throw 'NSSM is required to register the service. Install it with: winget install --id NSSM.NSSM -e'
    }
}

function Require-Java {
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

function Install-Files {
    New-Item -ItemType Directory -Force -Path $InstallDir, $DataDir | Out-Null
    $target = Join-Path $InstallDir 'rodeo.jar'
    if ((Resolve-Path $Jar).Path -ne $target) {
        Copy-Item -Force -Path $Jar -Destination $target
    }
    if (-not (Test-Path $ConfigFile)) {
        if ($Config) {
            Copy-Item -Path $Config -Destination $ConfigFile
        } else {
            Copy-Item -Path (Join-Path $Here 'config.example.toml') -Destination $ConfigFile
            Write-Host "edit $ConfigFile and set the webhook secrets before starting rodeo"
        }
    }
}

function Register-Service {
    param([string]$JavaPath)
    $arguments = "-jar `"$(Join-Path $InstallDir 'rodeo.jar')`" --rodeo.config=`"$ConfigFile`""
    & $script:Nssm install $ServiceName $JavaPath $arguments | Out-Null
    & $script:Nssm set $ServiceName Description 'rodeo pipeline runner' | Out-Null
    & $script:Nssm set $ServiceName AppDirectory $InstallDir | Out-Null
    & $script:Nssm set $ServiceName Start SERVICE_AUTO_START | Out-Null
    & $script:Nssm set $ServiceName AppExit Default Restart | Out-Null
}

function Install-Rodeo {
    Require-Administrator
    Require-Jar
    Require-Nssm
    $java = Require-Java
    Install-Files
    Register-Service -JavaPath $java
    & $script:Nssm start $ServiceName
}

function Upgrade-Rodeo {
    Require-Administrator
    Require-Jar
    Require-Nssm
    & $script:Nssm stop $ServiceName | Out-Null
    Copy-Item -Force -Path $Jar -Destination (Join-Path $InstallDir 'rodeo.jar')
    & $script:Nssm start $ServiceName
}

function Uninstall-Rodeo {
    Require-Administrator
    Require-Nssm
    & $script:Nssm stop $ServiceName | Out-Null
    & $script:Nssm remove $ServiceName confirm | Out-Null
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
