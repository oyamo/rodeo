# rodeo

Push-triggered command runner for Git repositories. On a push to a configured branch, rodeo clones the branch and runs the commands declared in the repository's `rodeo.toml`, in order, on the host it runs on.

![Java](https://img.shields.io/badge/language-Java%2021-orange)
![Coverage](https://img.shields.io/badge/coverage-89%25-brightgreen)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.9-6DB33F)

## Example

```toml
branches = ["main"]

commands = [
  "mvn -B verify",
  "./scripts/deploy.sh",
]
```

Pushes to any branch not listed in `branches`, and tag pushes, are ignored.

## Quick start

```bash
export GITHUB_WEBHOOK_SECRET='...'
export GITLAB_WEBHOOK_SECRET='...'
mvn -B package
java -jar target/rodeo-0.1.0-SNAPSHOT.jar
```

Or with Docker:

```bash
docker build -t rodeo .
docker run -d -p 8080:8080 --env-file rodeo.env rodeo
```

Both secrets are required; the process exits at startup if either is unset.

## Configuration

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `GITHUB_WEBHOOK_SECRET` | yes | — | Secret for GitHub push webhooks |
| `GITLAB_WEBHOOK_SECRET` | yes | — | Secret token for GitLab push webhooks |
| `PORT` | no | `8080` | HTTP listen port |

Webhook endpoints: `POST /webhooks/github` and `POST /webhooks/gitlab`. Serve them over HTTPS behind a reverse proxy.

## `rodeo.toml`

| Key | Type | Behavior |
|---|---|---|
| `branches` | string array | Branches that trigger `commands`. Empty or missing means nothing runs. |
| `commands` | string array | Executed with `sh -c` from the clone root, sequentially. The first non-zero exit stops the list. |

Execution:

- Shallow clone: `git clone --depth 1 --branch <branch>` into a fresh temp directory, deleted after the run.
- Timeouts: 5 minutes for the clone, 10 minutes per command.
- Environment: cleared, then `PATH` and `HOME` restored. Server secrets are not visible to commands.
- Concurrency: one run at a time, FIFO, on a single worker thread.
- Failures are logged. Nothing is retried by rodeo.

## Security model

rodeo executes repository-defined commands with the privileges of the service user. Anyone with push access to a listed branch can run arbitrary commands on the host. Treat `rodeo.toml` and the scripts it calls as production code, and restrict push access to the listed branches accordingly.

There is no container or namespace isolation yet.

## Limitations

- Public repositories only; the clone sends no credentials.
- Single worker. Long-running commands delay every run queued behind them.
- No run history or status reporting. Output goes to stdout.
- Requires `git` and `sh` on the host or in the container image.

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
