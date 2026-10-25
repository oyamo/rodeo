# rodeo

rodeo receives push events from GitHub and GitLab, checks that each one really came from your provider, and then acts on it. It can run hooks inside the service, or run the commands listed in a `rodeo.toml` file in your repository. That file is how you deploy on push.

## Get started

1. [Install rodeo](installation/docker.md) with Docker, or on [Linux and macOS](installation/linux.md) or [Windows](installation/windows.md).
2. [Configure](configuration.md) the two webhook secrets.
3. [Run](running.md) the service and connect your repositories.
4. [Deploy with rodeo.toml](deployment.md) to run commands on every push.

## Requirements at a glance

| Requirement | Needed for |
|---|---|
| Java 21 or newer | Running rodeo outside Docker |
| Git | Cloning repositories for `rodeo.toml` commands |
| `sh` on the `PATH` | Running `rodeo.toml` commands (Git Bash on Windows) |
| A public HTTPS address | Receiving webhooks from GitHub or GitLab |

!!! warning "Commands run on your server"
    Every command in a `rodeo.toml` runs on the machine that runs rodeo, with that machine's permissions. Anyone who can push to a listed repository controls what runs. Only enable repositories you trust. See [rodeo.toml](rodeo-toml.md#security).
