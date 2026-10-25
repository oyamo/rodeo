# Configuration

rodeo is configured through environment variables. It reads no configuration file on the server.

## Reference

### Webhook authentication

| Variable | Required | Default | Description |
|---|---|---|---|
| `GITHUB_WEBHOOK_SECRET` | yes | none | Secret for GitHub webhooks. Used to verify the `X-Hub-Signature-256` header. |
| `GITLAB_WEBHOOK_SECRET` | yes | none | Secret token for GitLab webhooks. Compared with the `X-Gitlab-Token` header. |

rodeo does not start unless both are set. Use a different value for each provider.

### HTTP

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | Port the HTTP server listens on |

### Pipeline execution

| Variable | Default | Description |
|---|---|---|
| `RODEO_MAX_PARALLEL_STAGES` | `4` | Maximum number of stages that run at the same time across all pipelines and runs |
| `RODEO_ALLOWED_SECRETS` | empty | Comma-separated names of server secrets that `rodeo.toml` may request, for example `DEPLOY_TOKEN,REGISTRY_PASSWORD` |

A stage that lists a secret not in `RODEO_ALLOWED_SECRETS` is rejected before the run starts. The value of each allowed secret is read from the environment of the rodeo process and given only to the stages that request it.

```text
RODEO_ALLOWED_SECRETS=DEPLOY_TOKEN,REGISTRY_PASSWORD
```

## Generating secrets

Use at least 32 random bytes:

```bash
openssl rand -hex 32
```

Enter the same value in the matching webhook settings. See [Running rodeo](running.md#connect-a-repository).

## Setting variables

| Where rodeo runs | How |
|---|---|
| Docker | `--env-file rodeo.env`. See [Docker](installation/docker.md). |
| systemd | `EnvironmentFile=` in the unit file. See [Linux and macOS](installation/linux.md). |
| Windows | Machine-level variables. See [Windows](installation/windows.md). |
| Terminal | `export NAME=value` (Bash) or `$env:NAME = "value"` (PowerShell) |

Variables are read at startup. After a change, restart rodeo.

When you rotate a webhook secret, update the variable, restart rodeo, and then update the webhook in the provider. Deliveries sent between the two steps are rejected with `401`.

## Logging

rodeo writes logs to standard output, which the service manager or container runtime captures. The default format is:

```text
HH:mm:ss.SSS LEVEL message
```

Log levels are set to `INFO` for `dev.rodeo`. Output format and the meaning of each message are described in [Logs](logs.md).

To change verbosity, set the level for the package in `application.yml` or pass a Spring Boot argument, for example `--logging.level.dev.rodeo=DEBUG`.

## Example

A production environment file:

```text
GITHUB_WEBHOOK_SECRET=...
GITLAB_WEBHOOK_SECRET=...
PORT=8080
RODEO_MAX_PARALLEL_STAGES=6
RODEO_ALLOWED_SECRETS=DEPLOY_TOKEN
```
