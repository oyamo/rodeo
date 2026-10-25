# Configuration

rodeo reads all of its configuration from one TOML file, named when rodeo starts.

## Starting rodeo with a configuration file

```bash
java -jar rodeo.jar --rodeo.config=/etc/rodeo/config.toml
```

If the argument is missing, rodeo cannot authenticate webhooks and refuses to start. If the file does not exist, rodeo stops with `config file not found`.

## The file

```toml
[server]
port = 8080

[github.webhook]
secret = "replace-with-a-long-random-string"

[gitlab.webhook]
secret = "replace-with-another-long-random-string"

[rodeo.status]
github-token = "github_pat_..."
github-api-url = "https://api.github.com"

[rodeo.exec]
max-parallel-stages = 4
allow-host-execution = false
stage-cpus = "1"
stage-memory = "1g"
path = "/usr/local/bin:/usr/bin:/bin"
home = "/var/lib/rodeo"

[rodeo.tools]
shell = "/bin/sh"
docker = "/usr/bin/docker"
git = "/usr/bin/git"

[rodeo.git]
token = "github_pat_..."

[rodeo.secrets]
DEPLOY_TOKEN = "..."
REGISTRY_PASSWORD = "..."
```

## Reference

### `[server]`

| Key | Default | Description |
|---|---|---|
| `port` | `8080` | Port the HTTP server listens on |

### `[github.webhook]` and `[gitlab.webhook]`

| Key | Required | Description |
|---|---|---|
| `secret` | yes | The secret configured on the provider's webhook. GitHub uses it to verify the `X-Hub-Signature-256` signature. GitLab sends it in `X-Gitlab-Token`. Use at least 32 random bytes. |

rodeo does not start unless both secrets are set. Use a different value for each provider.

### `[rodeo.status]`

| Key | Default | Description |
|---|---|---|
| `github-token` | none | Token that lets rodeo post commit statuses. Without it, no status is posted. See [Commit status reporting](commit-status.md). |
| `github-api-url` | `https://api.github.com` | GitHub API base URL, for GitHub Enterprise Server |

The token is read by rodeo itself. Stage commands never receive it.

### `[rodeo.exec]`

| Key | Default | Description |
|---|---|---|
| `max-parallel-stages` | `4` | Maximum number of stages that run at the same time within a run |
| `allow-host-execution` | `false` | Whether stages with no image may run directly on the server. When `false`, such stages are rejected. See [Where a stage runs](rodeo-toml/execution.md#where-a-stage-runs). |
| `stage-cpus` | `"1"` | CPU limit for each stage container |
| `stage-memory` | `"1g"` | Memory limit for each stage container, such as `512m` or `2g` |
| `path` | `/usr/local/bin:/usr/bin:/bin` | `PATH` given to commands that run on the server, and to the Docker client |
| `home` | `/var/lib/rodeo` | `HOME` given to commands that run on the server, and to the Docker client |
| `default-image` | none | Container image used by any stage that does not name one. Set it so pipeline files can omit `image`. |

### `[rodeo.tools]`

| Key | Default | Description |
|---|---|---|
| `shell` | `sh` | Shell that runs stage commands |
| `docker` | `docker` | Docker client used for container stages |
| `git` | `git` | Git executable used to clone repositories |

The defaults are bare names, which the operating system resolves through the `PATH` of the rodeo process. For a fully explicit setup, give absolute paths, as in the example above.

### `[rodeo.git]`

Lets rodeo clone private repositories. Set one of these two keys, or neither for public repositories only.

| Key | Description |
|---|---|
| `token` | A token with read access to the repositories, such as a GitHub fine-grained token or a GitLab token. rodeo clones over HTTPS and sends the token through a credentials file that exists only for the duration of the clone. |
| `ssh-key` | Path to a read-only SSH deploy key on the server. rodeo clones the repository over SSH, and never sends the key anywhere else. |

When `ssh-key` is set, rodeo uses SSH for every clone, and `token` is not used. Add the matching public key as a deploy key on each repository.

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
