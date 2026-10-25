# rodeo

Push-triggered pipeline runner for Git repositories. On a push to a configured branch, rodeo clones the branch and runs the stages declared in the repository's `rodeo.toml`. Each stage runs in a container, with no network and no access to the server unless the stage asks for it.

![Java](https://img.shields.io/badge/language-Java%2021-orange)
![Coverage](https://img.shields.io/badge/coverage-89%25-brightgreen)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.9-6DB33F)

## Example

```toml
on = ["main"]

[frontend]
build  = "npm ci && npm run build"
deploy = { run = "./scripts/deploy-frontend.sh", needs = ["build", "backend.deploy"] }

[backend]
build  = "mvn -B verify"
deploy = { run = "./scripts/deploy-backend.sh", needs = ["build"] }
```

Pushes to any branch not listed in `branches`, and tag pushes, are ignored.

## Quick start

```bash
mvn -B package
java -jar target/rodeo-0.1.0-SNAPSHOT.jar --rodeo.config=/etc/rodeo/config.toml
```

Or with Docker:

```bash
docker build -t rodeo .
docker run -d -p 8080:8080 -v /etc/rodeo/config.toml:/etc/rodeo/config.toml:ro rodeo
```

Both webhook secrets are required. rodeo reads all configuration from the TOML file named with `--rodeo.config`, and exits at startup if a required value is missing.

## Configuration

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `[github.webhook] secret` | yes | — | Secret for GitHub push webhooks |
| `[gitlab.webhook] secret` | yes | — | Secret token for GitLab push webhooks |
| `[server] port` | no | `8080` | HTTP listen port |

Webhook endpoints: `POST /webhooks/github` and `POST /webhooks/gitlab`. Serve them over HTTPS behind a reverse proxy.

## `rodeo.toml`

| Key | Type | Behavior |
|---|---|---|
| `branches` | string array | Branches that trigger `commands`. Empty or missing means nothing runs. |
| `commands` | string array | Executed with `sh -c` from the clone root, sequentially. The first non-zero exit stops the list. |

Execution:

- Shallow clone: `git clone --depth 1 --branch <branch>` into a fresh temp directory, deleted after the run.
- Timeouts: 5 minutes for the clone, 10 minutes per command.
- Commands run with a cleared set of variables. Only `PATH` and `HOME` from `[rodeo.exec]` are passed, plus the stage's own variables and the secrets it requests.
- Concurrency: one run at a time, FIFO, on a single worker thread.
- Failures are logged. Nothing is retried by rodeo.

## Security model

Stages run in containers by default. Each command gets a new container with only the run's checkout mounted, no network unless the stage asks for one, fixed CPU and memory limits, all Linux capabilities dropped, and no access to the server's files. Secrets reach a stage only when the stage requests them and the server allows the name.

A stage with no image runs directly on the server, and the server operator must allow that with `allow-host-execution = true` under `[rodeo.exec]`.

The service account must be able to run containers, and membership of the `docker` group is equivalent to root. Prefer rootless Docker.

## Limitations

- Public repositories only; the clone sends no credentials.
- Single worker. Long-running commands delay every run queued behind them.
- No run history. Output goes to stdout. GitHub commit statuses are reported when `github-token` is set under `[rodeo.status]`.
- Requires `git` on the server, and a container runtime for container stages.

## Build and test

```bash
mvn -B verify          # tests and JaCoCo report in target/site/jacoco/
```

Source layout:

| Package | Contents |
|---|---|
| `controller` | HTTP endpoints for each provider |
| `service` | Event handling and dispatch |
| `security` | Webhook authentication |
| `exec` | Clone, `rodeo.toml` parsing, command execution |
| `model` | Normalized push event and provider payloads |
| `config` | Configuration properties and Spring Security |

## Documentation

Full docs: [`docs/`](docs/index.md). Installation guides for [Docker](docs/installation/docker.md), [Linux and macOS](docs/installation/linux.md), and [Windows](docs/installation/windows.md). [Configuration](docs/configuration.md), [`rodeo.toml` reference](docs/rodeo-toml.md), and [deployment walkthrough](docs/deployment.md).
