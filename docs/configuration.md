# Configuration

rodeo is configured with environment variables. There is no configuration file to edit.

## Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `GITHUB_WEBHOOK_SECRET` | Yes | none | Secret shared with the GitHub webhook. Used to verify the `X-Hub-Signature-256` signature. |
| `GITLAB_WEBHOOK_SECRET` | Yes | none | Secret token shared with the GitLab webhook. Sent in `X-Gitlab-Token`. |
| `PORT` | No | `8080` | Port the HTTP server listens on. |

rodeo will not start unless both secrets are set. If one is missing, the log shows:

```text
GITHUB_WEBHOOK_SECRET must be set; refusing to accept unauthenticated webhooks
```

## Choosing secrets

Use a random value of at least 32 bytes. Generate one with:

```bash
openssl rand -hex 32
```

Use a different value for each provider. Enter the same value in the matching webhook settings. See [Running rodeo](running.md#connect-a-repository).

## Setting the variables

| Where rodeo runs | How to set them |
|---|---|
| Docker | `--env-file rodeo.env`, see [Docker](installation/docker.md) |
| Linux or macOS service | `EnvironmentFile=` in the systemd unit, see [Linux and macOS](installation/linux.md) |
| Windows | Machine-level variables, see [Windows](installation/windows.md) |
| Terminal, for testing | `export GITHUB_WEBHOOK_SECRET=...` (Bash) or `$env:GITHUB_WEBHOOK_SECRET = "..."` (PowerShell) |

!!! note "Changing a secret"
    rodeo reads the secrets at startup. After you change one, restart rodeo and update the matching webhook in your provider. Deliveries sent in between are rejected with `401`.
