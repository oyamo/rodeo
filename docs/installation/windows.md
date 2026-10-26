# Install on Windows

This page installs the prerequisites, builds the rodeo jar, and checks the installation. Run the commands in PowerShell. To run rodeo, see [Running rodeo](../running.md).

## Prerequisites

Install Java 21, Maven, and Git with winget:

```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK -e
winget install --id Apache.Maven -e
winget install --id Git.Git -e
```

Open a new PowerShell window once the installs finish.

## Point rodeo at `sh` and Git

rodeo runs stage commands with `sh`, and clones repositories with `git`. Git for Windows provides both under `C:\Program Files\Git\bin`. Put their full paths in the `[rodeo.tools]` section of the configuration file, not in the system `PATH`:

```toml
[rodeo.tools]
shell = "C:/Program Files/Git/bin/sh.exe"
git = "C:/Program Files/Git/cmd/git.exe"
```

See [Configuration](../configuration.md#rodeotools).

## Build the jar

Clone the repository and build the jar:

```powershell
git clone https://github.com/oyamo/rodeo.git
cd rodeo
mvn -B package
```

The jar is written to `target\rodeo-0.1.0-SNAPSHOT.jar`.

## Verify the installation

```powershell
java -version
mvn -version
git --version
```

Each command should print a version. Java must be version 21 or later.

## Next steps

- [Configuration](../configuration.md): the configuration file rodeo reads at startup
- [Running rodeo](../running.md): start rodeo and connect repositories
