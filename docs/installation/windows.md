# Install on Windows

This page installs rodeo as a Windows service from the MSI installer on the release page. The installer copies the files, creates the configuration file, and registers the service. Stage commands run in PowerShell.

## Prerequisites

Install these system-wide, so the service can find them. Run this in an elevated PowerShell window:

```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK -e --scope machine
winget install --id Git.Git -e --scope machine
```

Open a new elevated window afterwards so the new `PATH` is loaded.

## Install

Download `rodeo-<version>-x64.msi` from the release page and double-click it, or run it from an elevated window:

```powershell
msiexec /i rodeo-<version>-x64.msi
```

The installer:

- copies rodeo to `C:\Program Files\rodeo`
- creates `C:\ProgramData\rodeo\config.toml` from the example, if it doesn't exist
- registers and starts the `rodeo` service, which runs as the local system account

Set the webhook secrets in `C:\ProgramData\rodeo\config.toml`, then restart the service from an elevated window:

```powershell
Restart-Service rodeo
```

rodeo detects PowerShell, Git, and the Windows system folder on its own, so the configuration only needs your secrets and your choice of host execution. See [Configuration](../configuration.md).

## Check it

```powershell
Get-Service rodeo
```

## Upgrade

Download the new MSI and run it. The installer replaces the files and restarts the service. The configuration is kept.

## Uninstall

Remove rodeo from **Settings > Apps > Installed apps**, or run:

```powershell
msiexec /x rodeo-<version>-x64.msi
```

This removes the service and the program files. It keeps `C:\ProgramData\rodeo`, so your configuration survives a reinstall.
