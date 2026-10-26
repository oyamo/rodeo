# Install on Windows

This page installs rodeo directly on Windows, with stage commands running in PowerShell. Use it when your deploys run on the Windows machine itself. To run rodeo in a container instead, see [Install with Docker](docker.md).

## Prerequisites

Install these with winget. Run the commands in PowerShell.

```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK -e
winget install --id Apache.Maven -e
winget install --id Git.Git -e
winget install --id Microsoft.PowerShell -e
```

Open a new PowerShell window once the installs finish, then check the versions:

```powershell
java -version
mvn -version
git --version
pwsh -Version
```

## Build the jar

Clone the repository and build the jar:

```powershell
git clone https://github.com/oyamo/rodeo.git
cd rodeo
mvn -B package
```

The jar is written to `target\rodeo-0.1.0-SNAPSHOT.jar`. Copy it to a fixed location, so the service does not depend on the source tree:

```powershell
New-Item -ItemType Directory -Force C:\rodeo | Out-Null
Copy-Item target\rodeo-0.1.0-SNAPSHOT.jar C:\rodeo\rodeo.jar
```

## Configure

Create `C:\rodeo\config.toml`. rodeo detects the platform, so the file only needs your webhook secrets and the host execution setting:

```toml
[server]
port = 8080

[github.webhook]
secret = "replace-with-a-long-random-string"

[gitlab.webhook]
secret = "replace-with-another-long-random-string"

[rodeo.exec]
allow-host-execution = true
```

Stages on Windows run on the host, so `allow-host-execution` is `true`. rodeo finds the shell (PowerShell 7 if it's installed, otherwise Windows PowerShell), Git, the `PATH`, the home directory, and the Windows system folder on its own. See [Configuration](../configuration.md) for every key.

## Run

Start rodeo in a PowerShell window:

```powershell
java -jar C:\rodeo\rodeo.jar --rodeo.config=C:\rodeo\config.toml
```

Keep the window open while rodeo runs. The log prints one line per run and per stage.

## Run as a service

To keep rodeo running after a restart, register it as a Windows service with [NSSM](https://nssm.cc/):

```powershell
winget install --id NSSM.NSSM -e
nssm install rodeo "C:\Program Files\Eclipse Adoptium\jdk-21\bin\java.exe" "-jar C:\rodeo\rodeo.jar --rodeo.config=C:\rodeo\config.toml"
nssm start rodeo
```

The service account must be able to read `C:\rodeo\config.toml` and must reach your repositories.

## Write Windows stage commands

Commands run in PowerShell, so they use PowerShell syntax. A command that calls an executable must check its exit code, so that a failure stops the stage:

```toml
[app]
build = "dotnet publish -c Release -o C:/apps/app; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }"
deploy = "Restart-Service app"
```

## Verify the installation

```powershell
java -version
git --version
pwsh -Command "Write-Output ok"
```

Each command prints a version, or `ok`.

## Next steps

- [Configuration](../configuration.md): every configuration key
- [Running rodeo](../running.md): connect repositories and check deliveries
