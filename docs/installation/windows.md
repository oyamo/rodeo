# Install on Windows

These steps install rodeo directly on Windows, without Docker. Run the commands in PowerShell.

## 1. Install the prerequisites

Install the tools with winget:

```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK -e
winget install --id Apache.Maven -e
winget install --id Git.Git -e
```

Open a new PowerShell window so the new `PATH` entries load, then confirm:

```powershell
java -version
mvn -version
git --version
```

!!! important "Git Bash provides `sh`"
    rodeo runs `rodeo.toml` commands with `sh`. Git for Windows includes `sh` in its `bin` folder, which is not on `PATH` by default. Add it:

    ```powershell
    [Environment]::SetEnvironmentVariable(
        "Path",
        [Environment]::GetEnvironmentVariable("Path", "Machine") + ";C:\Program Files\Git\bin",
        "Machine")
    ```

    Open a new PowerShell window afterwards and check with `sh -c "echo ok"`.

## 2. Build the jar

From the project root:

```powershell
mvn -B package
```

The jar is written to `target\rodeo-0.1.0-SNAPSHOT.jar`.

## 3. Set the secrets

Set the variables for the machine, so they persist across restarts:

```powershell
[Environment]::SetEnvironmentVariable("GITHUB_WEBHOOK_SECRET", "replace-with-a-long-random-string", "Machine")
[Environment]::SetEnvironmentVariable("GITLAB_WEBHOOK_SECRET", "replace-with-another-long-random-string", "Machine")
```

Open a new PowerShell window before you run rodeo, so it picks them up.

## 4. Run

```powershell
java -jar target\rodeo-0.1.0-SNAPSHOT.jar
```

To run it in the background at startup, register it as a Windows service with a wrapper such as [NSSM](https://nssm.cc/), pointing the service at `java` with the arguments `-jar C:\rodeo\rodeo-0.1.0-SNAPSHOT.jar`. The service must run under an account that has the `Machine` variables and can reach your repositories.

## Commands in `rodeo.toml`

Commands run through `sh`, so they use POSIX shell syntax such as `&&`, `>` and `./script.sh`. Tools such as `mvn` must be on the system `PATH`. PowerShell scripts run as `pwsh -File deploy.ps1` if you call them from `rodeo.toml`.

## Next steps

Continue with [Configuration](../configuration.md) and [Running rodeo](../running.md).
