# Install on Windows

This page installs the prerequisites, builds the rodeo jar, and checks the installation. Run the commands in PowerShell. To run rodeo, see [Running rodeo](../running.md).

## Prerequisites

Install Java 21, Maven, and Git with winget:

```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK -e
winget install --id Apache.Maven -e
winget install --id Git.Git -e
```

Open a new PowerShell window so the updated `PATH` takes effect.

## Make `sh` available

rodeo runs stage commands with `sh`. Git for Windows provides `sh` in its `bin` folder, which is not on `PATH` by default. Add it to the machine `PATH`:

```powershell
[Environment]::SetEnvironmentVariable(
    "Path",
    [Environment]::GetEnvironmentVariable("Path", "Machine") + ";C:\Program Files\Git\bin",
    "Machine")
```

Open a new PowerShell window and check:

```powershell
sh -c "echo ok"
```

## Build the jar

From the project root:

```powershell
mvn -B package
```

The jar is written to `target\rodeo-0.1.0-SNAPSHOT.jar`.

## Verify the installation

```powershell
java -version
mvn -version
git --version
sh -c "echo ok"
```

Each command should print a version or `ok`. Java must be version 21 or later.

## Next steps

- [Configuration](../configuration.md): the variables rodeo needs before it starts
- [Running rodeo](../running.md): start rodeo and connect repositories
