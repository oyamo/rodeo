# Configuration

rodeo reads all of its configuration from one TOML file, named when rodeo starts.

## Starting rodeo with a configuration file

```bash
java -jar rodeo.jar --rodeo.config=/etc/rodeo/config.toml
```

If the argument is missing, rodeo cannot authenticate webhooks and refuses to start. If the file does not exist, rodeo stops with `config file not found`.

## The file

```toml
[github]
webhook-secret = "replace-with-a-long-random-string"
token = "github_pat_..."
api-url = "https://api.github.com"

[gitlab]
webhook-secret = "replace-with-another-long-random-string"
token = "glpat-..."

[rodeo]
port = 8080
allow-host-execution = false

[rodeo.stages]
cpus = "1"
memory = "1g"

[rodeo.secrets]
DEPLOY_TOKEN = "..."
REGISTRY_PASSWORD = "..."
```

## Reference

### `[github]`

| Key | Required | Default | Description |
|---|---|---|---|
| `webhook-secret` | yes | none | The secret configured on the GitHub webhook. rodeo uses it to verify the `X-Hub-Signature-256` signature. |
| `token` | no | none | Token rodeo uses to clone private repositories and to post commit statuses. Without it, only public repositories are cloned and no status is posted. See [Commit status reporting](commit-status.md). |
| `ssh-key` | no | none | Path to a read-only SSH deploy key on the server, used to clone repositories over SSH. When set, `token` is not used for cloning. |
| `api-url` | no | `https://api.github.com` | GitHub API base URL, for GitHub Enterprise Server |

The token is read by rodeo itself. Stage commands never receive it.

### `[gitlab]`

| Key | Required | Description |
|---|---|---|
| `webhook-secret` | yes | The secret configured on the GitLab webhook. GitLab sends it in `X-Gitlab-Token`. |
| `token` | no | Token rodeo uses to clone private repositories. Without it, only public repositories are cloned. |
| `ssh-key` | no | Path to a read-only SSH deploy key on the server, used to clone repositories over SSH. When set, `token` is not used for cloning. |

rodeo does not start unless both webhook secrets are set. Use a different value for each provider, at least 32 random bytes long.

### `[rodeo]`

| Key | Default | Description |
|---|---|---|
| `port` | `8080` | Port the HTTP server listens on |
| `max-parallel-stages` | number of CPUs | Maximum number of stages that run at the same time within a run |
| `allow-host-execution` | `false` | Whether stages with no image may run directly on the server. When `false`, such stages are rejected. See [Where a stage runs](rodeo-toml/execution.md#where-a-stage-runs). |

### `[rodeo.stages]`

| Key | Default | Description |
|---|---|---|
| `default-image` | none | Container image used by any stage that does not name one. Set it so pipeline files can omit `image`. |
| `cpus` | `"1"` | CPU limit for each stage container |
| `memory` | `"1g"` | Memory limit for each stage container, such as `512m` or `2g` |

### `[rodeo.secrets]`

Each key is the name of a secret, and its value is the secret. A stage can use a secret only by listing the name in its `secrets` option. A name that is not defined here is rejected when the file is parsed, before any stage starts. See [Environment and secrets](rodeo-toml/environment-and-secrets.md#secrets).

Secret values are given to the stages that request them, and they are never written to the log by rodeo.

## Keeping the file safe

The file contains secrets, so restrict who can read it:

```bash
sudo mkdir -p /etc/rodeo
sudo chown root:rodeo /etc/rodeo/config.toml
sudo chmod 640 /etc/rodeo/config.toml
```

The rodeo service user must be able to read it, and nobody else should.

## Rotating a secret

Change the value in the file, restart rodeo, and update the matching webhook in the provider. Deliveries sent between the two steps are rejected with `401`.
