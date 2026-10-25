# rodeo

rodeo runs your deployment pipelines when code is pushed. Pipelines are declared in a `rodeo.toml` file in the repository, and rodeo runs them on your own server.

```toml
on = ["main"]

[frontend]
build  = "npm ci && npm run build"
test   = { run = "npm test", needs = ["build"] }
deploy = { run = "./scripts/deploy-frontend.sh", needs = ["test", "backend.deploy"] }

[backend]
build  = "mvn -B package"
deploy = { run = "./scripts/deploy-backend.sh", needs = ["build"], secrets = ["DEPLOY_TOKEN"] }
```

## Features

- **Pipelines in one file.** Each top-level table is a pipeline. Pipelines run side by side.
- **Stage dependencies.** Stages declare what they depend on. Independent stages run in parallel, and dependencies can cross pipelines.
- **Conditions.** Stages run on success, on failure, or always. Rollback and cleanup are one line each.
- **Retries, timeouts, and optional stages.** Each stage can retry, time out, or be allowed to fail.
- **Server-controlled secrets.** A repository can request only the secrets the server allows.
- **Readable logs.** Every line carries the run and stage it belongs to, and each run ends with a summary table.

## Get started

1. [Install](installation/docker.md) with Docker, or on [Linux and macOS](installation/linux.md) or [Windows](installation/windows.md).
2. [Configure](configuration.md) the webhook secrets and execution limits.
3. [Run](running.md) the service and connect repositories.
4. Write a [rodeo.toml](rodeo-toml/index.md) and follow the [deployment guide](deployment.md).

## Documentation

| Topic | Page |
|---|---|
| Installation | [Docker](installation/docker.md), [Linux and macOS](installation/linux.md), [Windows](installation/windows.md) |
| Configuration | [Environment variables](configuration.md) |
| Operations | [Running rodeo](running.md), [Logs](logs.md) |
| Pipeline definition | [rodeo.toml reference](rodeo-toml/index.md), [Examples](examples.md) |
| Guides | [Deploy on push](deployment.md) |

## Requirements

| Requirement | Used for |
|---|---|
| Java 21 or later | Running rodeo outside Docker |
| Git | Cloning repositories |
| `sh` | Running stage commands. On Windows, use Git Bash. |
| A public HTTPS address | Receiving webhooks from GitHub or GitLab |

!!! warning "Stage commands run on your server"
    Commands in `rodeo.toml` run on the machine that runs rodeo, with that machine's permissions. Anyone who can push to a listed branch controls what runs. Enable only repositories you trust. See [Security](rodeo-toml/index.md#security).
